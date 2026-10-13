package org.vmstudio.visor.api.client.input;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Input hooks for addons
 */
public final class VisorInput {

    private static final List<BooleanSupplier> LOCOMOTION_LOCKS = new CopyOnWriteArrayList<>();

    private VisorInput() {
        throw new UnsupportedOperationException("This is an utility class and cannot be instantiated");
    }

    /**
     * While any lock returns true, Visor's snap / smooth turn is disabled
     * (e.g. the sticks drive something else). Checked live every tick
     */
    public static void addLocomotionLock(@NotNull BooleanSupplier lock) {
        LOCOMOTION_LOCKS.add(lock);
    }

    public static boolean isLocomotionLocked() {
        for (BooleanSupplier lock : LOCOMOTION_LOCKS) {
            if (lock.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }
}
