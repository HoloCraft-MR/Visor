package org.vmstudio.visor.loader.fabric;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class WorldPreloaderClient {
    private WorldPreloaderClient() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (WorldPreloader.isActive()
                    && client.level != null
                    && client.player != null
                    && !(client.screen instanceof WorldPreloaderScreen)) {
                client.setScreen(new WorldPreloaderScreen());
            }
        });
    }
}
