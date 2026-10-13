package org.vmstudio.visor.core.client.provider.openxr.render;

import me.phoenixra.atumvr.core.session.platform.XRPlatform;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.VisorClientImpl;
import org.vmstudio.visor.core.client.provider.openxr.XrProvider;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Crisp UI layers": UI textures submitted as {@code XrCompositionLayerQuad}s in the app space
 * (the projection layer's space) instead of being drawn into the eye buffers.
 * <ul>
 *     <li>producers (e.g. addon UI panels) check {@link #isEnabled()} before the eye
 *     passes, skip their in-world quad and {@link #request} a layer once their texture is ready;</li>
 *     <li>{@link #putLayers} (from {@link XrRenderer#finishFrame}) copies each texture into its own
 *     swapchain (glBlitFramebuffer, one swapchain per producer, recreated on size change, freed when
 *     unused) and appends the quads after the projection layer: passthrough &lt; projection &lt; quads.
 *     Quads therefore always cover the projection layer (hands / world can't occlude them);</li>
 *     <li>{@code XR_FB_composition_layer_settings}, when enabled, adds normal supersampling +
 *     sharpening per quad;</li>
 *     <li>any OpenXR / GL failure disables the layers for the session ({@link #isEnabled()} false),
 *     producers fall back to in-world drawing from the next frame.</li>
 * </ul>
 */
public final class XrQuadLayers {

    /** Quest allows 16 layers; passthrough + projection + this many quads */
    public static final int MAX_LAYERS = 10;
    /** Cursor dot: texture size (px) and quad size (m) */
    private static final int CURSOR_PX = 32;
    private static final float CURSOR_METERS = 0.012f;
    /** Frames a swapchain may go unrequested before it is destroyed */
    private static final int UNUSED_FRAMES = 300;
    private static final String SETTINGS_EXT = FBCompositionLayerSettings.XR_FB_COMPOSITION_LAYER_SETTINGS_EXTENSION_NAME;
    private static final long SETTINGS_FLAGS = FBCompositionLayerSettings.XR_COMPOSITION_LAYER_SETTINGS_NORMAL_SUPER_SAMPLING_BIT_FB
            | FBCompositionLayerSettings.XR_COMPOSITION_LAYER_SETTINGS_NORMAL_SHARPENING_BIT_FB;

    /** Off by default; turned on by whoever submits UI as quads (e.g. an addon) */
    private static boolean enabled;
    private static boolean failed;
    private static boolean supportLogged;
    private static boolean settingsSupported;
    private static long format;

    private static int cursorTexture, cursorFramebuffer;
    private static final List<Request> requests = new ArrayList<>();
    private static final Map<Object, Layer> layers = new IdentityHashMap<>();

    private XrQuadLayers() {
    }

    /** Turned on, OpenXR provider running, no failure this session */
    public static boolean isEnabled() {
        return !failed && enabled
                && ClientContext.visor != null
                && ClientContext.visor.getVrProvider() instanceof XrProvider provider
                && provider.getSession() != null;
    }

    /** Turns quad layers on or off */
    public static void setEnabled(boolean on) {
        if (enabled == on) {
            return;
        }
        enabled = on;
        VisorClientImpl.LOGGER.info("Quad layers: {}{}", on ? "on" : "off, in-world UI",
                on && failed ? ", but failed earlier this session: in-world UI" : "");
    }

    /** Remaining quads this frame */
    public static int remaining() {
        return MAX_LAYERS - requests.size();
    }

    /**
     * Requests a quad for this frame (render thread, after the texture is drawn, before
     * {@link XrRenderer#finishFrame})
     *
     * @param key           producer identity (keeps its swapchain across frames)
     * @param label         for logs
     * @param framebuffer   GL framebuffer holding the texture (color attachment 0)
     * @param width         texture width
     * @param height        texture height
     * @param position      quad center, app (room) space, meters
     * @param orientation   quad orientation, app space; the texture faces +z, +x right, +y up
     * @param sizeX         quad width, meters
     * @param sizeY         quad height, meters
     * @param premultiplied texture colors are premultiplied by alpha
     * @return false when full or disabled (draw in-world instead)
     */
    public static boolean request(@NotNull Object key, @NotNull String label, int framebuffer, int width, int height,
                                  @NotNull Vector3fc position, @NotNull Quaternionfc orientation,
                                  float sizeX, float sizeY, boolean premultiplied) {
        if (!isEnabled() || requests.size() >= MAX_LAYERS || width <= 0 || height <= 0
                || !(sizeX > 0f) || !(sizeY > 0f)) {
            return false;
        }
        requests.add(new Request(key, label, framebuffer, width, height,
                position.x(), position.y(), position.z(),
                orientation.x(), orientation.y(), orientation.z(), orientation.w(),
                sizeX, sizeY, premultiplied));
        return true;
    }

    /** Cursor dot quad (after the overlay it points at), {@link #request} rules */
    public static boolean requestCursor(@NotNull Object hand, @NotNull Vector3fc position,
                                        @NotNull Quaternionfc orientation) {
        if (!isEnabled()) {
            return false;
        }
        if (cursorFramebuffer == 0) {
            createCursorTexture();
        }
        return request(hand, "cursor_" + hand.toString().toLowerCase(Locale.ROOT), cursorFramebuffer,
                CURSOR_PX, CURSOR_PX, position, orientation, CURSOR_METERS, CURSOR_METERS, true);
    }

    /** White dot, dark ring, soft edge, premultiplied */
    private static void createCursorTexture() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.ByteBuffer pixels = stack.malloc(CURSOR_PX * CURSOR_PX * 4);
            float c = (CURSOR_PX - 1) * 0.5f;
            for (int y = 0; y < CURSOR_PX; y++) {
                for (int x = 0; x < CURSOR_PX; x++) {
                    float d = (float) Math.hypot(x - c, y - c) / (CURSOR_PX * 0.5f);
                    float alpha = Math.max(0f, Math.min(1f, (1f - d) * 8f));
                    float white = Math.max(0f, Math.min(1f, (0.72f - d) * 8f));
                    byte v = (byte) Math.round(white * 255f);
                    pixels.put(v).put(v).put(v).put((byte) Math.round(alpha * 255f));
                }
            }
            pixels.flip();
            int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int prevFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            cursorTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, cursorTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, CURSOR_PX, CURSOR_PX, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            cursorFramebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, cursorFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                    cursorTexture, 0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFramebuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
        }
    }

    /**
     * Copies the requested textures and appends one quad per request to {@code out}
     * (after the projection layer); clears the requests
     */
    static void putLayers(@NotNull XrProvider provider, @NotNull MemoryStack stack, @NotNull PointerBuffer out) {
        int start = out.position();
        try {
            if (requests.isEmpty() || !isEnabled()) {
                return;
            }
            logSupport(provider);
            XrSession session = provider.getSession().getHandle();
            if (format == 0) {
                format = pickFormat(session, stack);
            }

            int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            try {
                for (Request request : requests) {
                    if (out.remaining() == 0) {
                        break;
                    }
                    Layer layer = layers.get(request.key);
                    if (layer != null && (layer.width != request.width || layer.height != request.height)) {
                        layer.destroy();
                        layer = null;
                    }
                    if (layer == null) {
                        layer = new Layer(request.label, request.width, request.height);
                        layer.create(session, stack);
                        layers.put(request.key, layer);
                    }
                    layer.unusedFrames = 0;
                    layer.copy(request.framebuffer, stack);
                    out.put(quad(provider, layer, request, stack));
                }
            } finally {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
                if (scissor) {
                    GL11.glEnable(GL11.GL_SCISSOR_TEST);
                }
            }
        } catch (Throwable t) {
            // drop this frame's quads: their swapchains are destroyed below
            out.position(start);
            fail(t.toString());
        } finally {
            requests.clear();
            sweep();
        }
    }

    private static XrCompositionLayerQuad quad(@NotNull XrProvider provider, @NotNull Layer layer,
                                               @NotNull Request r, @NotNull MemoryStack stack) {
        long flags = XR10.XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
        if (!r.premultiplied) {
            flags |= XR10.XR_COMPOSITION_LAYER_UNPREMULTIPLIED_ALPHA_BIT;
        }
        long next = 0L;
        if (settingsSupported) {
            next = XrCompositionLayerSettingsFB.calloc(stack)
                    .type(FBCompositionLayerSettings.XR_TYPE_COMPOSITION_LAYER_SETTINGS_FB)
                    .layerFlags(SETTINGS_FLAGS)
                    .address();
        }
        XrCompositionLayerQuad quad = XrCompositionLayerQuad.calloc(stack)
                .type(XR10.XR_TYPE_COMPOSITION_LAYER_QUAD)
                .next(next)
                .layerFlags(flags)
                .space(provider.getSession().getXrAppSpace())
                .eyeVisibility(XR10.XR_EYE_VISIBILITY_BOTH);
        quad.subImage().swapchain(layer.swapchain).imageArrayIndex(0);
        quad.subImage().imageRect().offset().set(0, 0);
        quad.subImage().imageRect().extent().set(layer.width, layer.height);
        quad.pose().orientation().set(r.qx, r.qy, r.qz, r.qw);
        quad.pose().position$().set(r.px, r.py, r.pz);
        quad.size().set(r.sizeX, r.sizeY);
        if (!layer.sizeLogged || Math.abs(layer.loggedSizeX - r.sizeX) > 0.05f) {
            layer.sizeLogged = true;
            layer.loggedSizeX = r.sizeX;
            VisorClientImpl.LOGGER.info(String.format(Locale.ROOT,
                    "Quad layers: quad %s %dx%d px, %.2f x %.2f m at (%.2f, %.2f, %.2f)%s",
                    r.label, layer.width, layer.height, r.sizeX, r.sizeY, r.px, r.py, r.pz,
                    r.premultiplied ? ", premultiplied" : ""));
        }
        return quad;
    }

    private static void logSupport(@NotNull XrProvider provider) {
        if (supportLogged) {
            return;
        }
        supportLogged = true;
        settingsSupported = provider.getSession().getInstance().isExtensionEnabled(SETTINGS_EXT);
        VisorClientImpl.LOGGER.info("Quad layers: quad layers on (max {}), {}={}{}", MAX_LAYERS, SETTINGS_EXT,
                settingsSupported, settingsSupported ? " (normal supersampling + sharpening)" : "");
    }

    private static long pickFormat(@NotNull XrSession session, @NotNull MemoryStack stack) {
        IntBuffer count = stack.callocInt(1);
        check(XR10.xrEnumerateSwapchainFormats(session, count, null), "xrEnumerateSwapchainFormats");
        LongBuffer formats = stack.callocLong(count.get(0));
        check(XR10.xrEnumerateSwapchainFormats(session, count, formats), "xrEnumerateSwapchainFormats");
        // alpha required; sRGB like the eye swapchain, else plain RGBA8 (both show the same values)
        for (long want : new long[]{GL30.GL_SRGB8_ALPHA8, GL11.GL_RGBA8}) {
            for (int i = 0; i < formats.capacity(); i++) {
                if (formats.get(i) == want) {
                    return want;
                }
            }
        }
        throw new IllegalStateException("no RGBA swapchain format");
    }

    /** Destroys swapchains not requested for a while */
    private static void sweep() {
        Iterator<Layer> it = layers.values().iterator();
        while (it.hasNext()) {
            Layer layer = it.next();
            if (++layer.unusedFrames > UNUSED_FRAMES) {
                layer.destroy();
                it.remove();
            }
        }
    }

    private static void fail(@NotNull String reason) {
        if (failed) {
            return;
        }
        failed = true;
        VisorClientImpl.LOGGER.warn("Quad layers: failed ({}), fallback to in-world UI for this session", reason);
        destroyAll();
    }

    /** Session end */
    public static void destroyAll() {
        for (Layer layer : layers.values()) {
            layer.destroy();
        }
        layers.clear();
        requests.clear();
        format = 0;
    }

    private static void check(int result, @NotNull String call) {
        if (result < 0) {
            throw new IllegalStateException(call + " -> " + result);
        }
    }

    private record Request(Object key, String label, int framebuffer, int width, int height,
                           float px, float py, float pz, float qx, float qy, float qz, float qw,
                           float sizeX, float sizeY, boolean premultiplied) {
    }

    private static final class Layer {
        final String label;
        final int width, height;
        XrSwapchain swapchain;
        int[] framebuffers = new int[0];
        int unusedFrames;
        boolean sizeLogged;
        float loggedSizeX;

        Layer(String label, int width, int height) {
            this.label = label;
            this.width = width;
            this.height = height;
        }

        void create(@NotNull XrSession session, @NotNull MemoryStack stack) {
            XrSwapchainCreateInfo info = XrSwapchainCreateInfo.calloc(stack)
                    .type(XR10.XR_TYPE_SWAPCHAIN_CREATE_INFO)
                    .usageFlags(XR10.XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR10.XR_SWAPCHAIN_USAGE_TRANSFER_DST_BIT)
                    .format(format)
                    .sampleCount(1)
                    .width(width)
                    .height(height)
                    .faceCount(1)
                    .arraySize(1)
                    .mipCount(1);
            PointerBuffer handle = stack.callocPointer(1);
            int result = XR10.xrCreateSwapchain(session, info, handle);
            if (result < 0) {
                VisorClientImpl.LOGGER.warn("Quad layers: swapchain {} {}x{} create failed ({})", label, width, height, result);
                throw new IllegalStateException("xrCreateSwapchain -> " + result);
            }
            swapchain = new XrSwapchain(handle.get(0), session);

            IntBuffer count = stack.callocInt(1);
            check(XR10.xrEnumerateSwapchainImages(swapchain, count, null), "xrEnumerateSwapchainImages");
            // GL / GLES image struct (same layout), typed per platform
            XrSwapchainImageOpenGLKHR.Buffer images = XRPlatform.get().createImageBuffers(count.get(0), stack);
            check(XR10.xrEnumerateSwapchainImages(swapchain, count,
                    XrSwapchainImageBaseHeader.create(images.address(), images.capacity())), "xrEnumerateSwapchainImages");

            int prev = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            framebuffers = new int[images.capacity()];
            for (int i = 0; i < framebuffers.length; i++) {
                framebuffers[i] = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffers[i]);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                        images.get(i).image(), 0);
                int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prev);
                    throw new IllegalStateException("layer framebuffer incomplete 0x" + Integer.toHexString(status));
                }
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prev);
            VisorClientImpl.LOGGER.info("Quad layers: swapchain {} {}x{} created ({} images, format 0x{})",
                    label, width, height, framebuffers.length, Long.toHexString(format));
        }

        /** Acquire, blit the source framebuffer's color, release */
        void copy(int source, @NotNull MemoryStack stack) {
            IntBuffer index = stack.callocInt(1);
            check(XR10.xrAcquireSwapchainImage(swapchain,
                    XrSwapchainImageAcquireInfo.calloc(stack).type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO),
                    index), "xrAcquireSwapchainImage");
            check(XR10.xrWaitSwapchainImage(swapchain,
                    XrSwapchainImageWaitInfo.calloc(stack).type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO)
                            .timeout(XR10.XR_INFINITE_DURATION)), "xrWaitSwapchainImage");
            try {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffers[index.get(0)]);
                GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                        GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            } finally {
                check(XR10.xrReleaseSwapchainImage(swapchain,
                        XrSwapchainImageReleaseInfo.calloc(stack).type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO)),
                        "xrReleaseSwapchainImage");
            }
        }

        void destroy() {
            for (int fb : framebuffers) {
                GL30.glDeleteFramebuffers(fb);
            }
            framebuffers = new int[0];
            if (swapchain != null) {
                XR10.xrDestroySwapchain(swapchain);
                swapchain = null;
            }
        }
    }
}
