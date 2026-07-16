package org.vmstudio.visor.loader.fabric;


import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public class VisorMod implements ModInitializer {
    @Override
    public void onInitialize() {
        WorldPreloader.initialize();
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            WorldPreloaderClient.initialize();
        }
    }
}
