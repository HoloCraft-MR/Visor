package org.vmstudio.visor.api.client.render.decoration.hand;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.vmstudio.visor.api.common.HandType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Replaces how the local player's own hand is drawn (GUI hand and first-person arm),
 * e.g. a custom hand model or no hand at all. Held items are still drawn by Visor.
 */
@FunctionalInterface
public interface HandRenderOverride {

    /**
     * @param poseStack at the hand (Visor's hand transform applied)
     * @param hand the hand
     * @return true if the hand was handled (Visor skips its own hand / arm)
     */
    boolean render(@NotNull PoseStack poseStack, @NotNull HandType hand);


    static void register(@NotNull HandRenderOverride override) {
        Registry.OVERRIDES.add(override);
    }

    /**
     * While any supplier returns true, GUI hands not aiming at a menu are depth-tested
     * (and write depth) instead of being drawn on top of everything, so they can be
     * occluded by / reach into world-space content drawn before them
     */
    static void addGuiHandDepthTest(@NotNull BooleanSupplier depthTested) {
        Registry.DEPTH_TESTED.add(depthTested);
    }

    @ApiStatus.Internal
    static boolean renderOverride(@NotNull PoseStack poseStack, @NotNull HandType hand) {
        for (HandRenderOverride override : Registry.OVERRIDES) {
            if (override.render(poseStack, hand)) {
                return true;
            }
        }
        return false;
    }

    @ApiStatus.Internal
    static boolean isGuiHandDepthTested() {
        for (BooleanSupplier supplier : Registry.DEPTH_TESTED) {
            if (supplier.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    @ApiStatus.Internal
    final class Registry {
        private static final List<HandRenderOverride> OVERRIDES = new CopyOnWriteArrayList<>();
        private static final List<BooleanSupplier> DEPTH_TESTED = new CopyOnWriteArrayList<>();

        private Registry() {
            throw new UnsupportedOperationException("This is an utility class and cannot be instantiated");
        }
    }
}
