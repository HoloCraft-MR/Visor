package org.vmstudio.visor.api.client;

import me.phoenixra.atumvr.core.enums.XRSessionState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Client lifecycle hooks for addons. Usable before the Visor client is created
 */
public final class VisorClientEvents {

    private static final List<Runnable> CLIENT_TICK = new CopyOnWriteArrayList<>();
    private static final List<Consumer<XRSessionState>> XR_SESSION_STATE = new CopyOnWriteArrayList<>();
    private static final List<Runnable> REFERENCE_SPACE_CHANGED = new CopyOnWriteArrayList<>();

    private VisorClientEvents() {
        throw new UnsupportedOperationException("This is an utility class and cannot be instantiated");
    }

    /**
     * Every client tick (Minecraft tick HEAD, right after Visor's VR tick, before the
     * player ticks), also when VR is off
     */
    public static void addClientTick(@NotNull Runnable callback) {
        CLIENT_TICK.add(callback);
    }

    /**
     * OpenXR session state changes (render thread)
     */
    public static void addXrSessionStateChanged(@NotNull Consumer<XRSessionState> callback) {
        XR_SESSION_STATE.add(callback);
    }

    /**
     * OpenXR reference space change pending (recenter, floor change)
     */
    public static void addReferenceSpaceChanged(@NotNull Runnable callback) {
        REFERENCE_SPACE_CHANGED.add(callback);
    }

    @ApiStatus.Internal
    public static void fireClientTick() {
        for (Runnable callback : CLIENT_TICK) {
            callback.run();
        }
    }

    @ApiStatus.Internal
    public static void fireXrSessionStateChanged(@NotNull XRSessionState state) {
        for (Consumer<XRSessionState> callback : XR_SESSION_STATE) {
            callback.accept(state);
        }
    }

    @ApiStatus.Internal
    public static void fireReferenceSpaceChanged() {
        for (Runnable callback : REFERENCE_SPACE_CHANGED) {
            callback.run();
        }
    }
}
