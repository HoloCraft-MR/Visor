package org.vmstudio.visor.api.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Render hooks for addons that draw their own content into the VR frame.
 * <p>
 *     Register once (e.g. in {@code VisorAddon#onAddonLoad}); all callbacks run on the render thread.
 * </p>
 */
public final class VisorRenderEvents {

    private static final List<BooleanSupplier> PASSTHROUGH_ACTIVE = new CopyOnWriteArrayList<>();
    private static final List<RenderPipelineCallback> BEFORE_GUI_HANDS = new CopyOnWriteArrayList<>();
    private static final List<Runnable> PRE_SUBMIT = new CopyOnWriteArrayList<>();
    private static final List<BooleanSupplier> CUSTOM_SCENE_DRAWING = new CopyOnWriteArrayList<>();

    private VisorRenderEvents() {
        throw new UnsupportedOperationException("This is an utility class and cannot be instantiated");
    }

    /**
     * Passthrough (mixed reality): while any supplier returns true, the eye passes clear to
     * alpha 0 and the eye post-process keeps the alpha, so the passthrough layer shows where
     * nothing was drawn
     */
    public static void addPassthroughActive(@NotNull BooleanSupplier active) {
        PASSTHROUGH_ACTIVE.add(active);
    }

    public static boolean isPassthroughActive() {
        for (BooleanSupplier supplier : PASSTHROUGH_ACTIVE) {
            if (supplier.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    /**
     * AFTER_WORLD stage of every VR render pass, before Visor's GUI hands, cursor and HUD:
     * those are drawn after the callback and can depth-test against what it drew
     */
    public static void addBeforeGuiHands(@NotNull RenderPipelineCallback callback) {
        BEFORE_GUI_HANDS.add(callback);
    }

    @ApiStatus.Internal
    public static void fireBeforeGuiHands(@NotNull PoseStack poseStack, float partialTicks) {
        for (RenderPipelineCallback callback : BEFORE_GUI_HANDS) {
            callback.render(poseStack, partialTicks);
        }
    }

    /**
     * Once per VR frame, after the overlay textures and before Visor's overlay quad layers and
     * the eye passes: render offscreen textures, request composition layers
     */
    public static void addPreSubmit(@NotNull Runnable callback) {
        PRE_SUBMIT.add(callback);
    }

    @ApiStatus.Internal
    public static void firePreSubmit() {
        for (Runnable callback : PRE_SUBMIT) {
            callback.run();
        }
    }

    /**
     * While any supplier returns true an addon draws its own scene with vanilla renderers
     * (e.g. entities in a miniature): the local player is drawn as a normal third-person
     * body there, not as Visor's first-person self model
     */
    public static void addCustomSceneDrawing(@NotNull BooleanSupplier drawing) {
        CUSTOM_SCENE_DRAWING.add(drawing);
    }

    public static boolean isDrawingCustomScene() {
        for (BooleanSupplier supplier : CUSTOM_SCENE_DRAWING) {
            if (supplier.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }
}
