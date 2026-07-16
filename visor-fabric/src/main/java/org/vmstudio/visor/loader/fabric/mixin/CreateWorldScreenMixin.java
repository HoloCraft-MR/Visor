package org.vmstudio.visor.loader.fabric.mixin;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.loader.fabric.WorldPreloader;

@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {
    @Inject(method = "onCreate", at = @At("HEAD"))
    private void visor$prepareWorldPreload(CallbackInfo ci) {
        WorldPreloader.markNewWorld();
    }
}
