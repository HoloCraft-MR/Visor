package org.vmstudio.visor.loader.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.ChunkyProvider;
import org.popcraft.chunky.api.ChunkyAPI;
import org.popcraft.chunky.api.event.task.GenerationCompleteEvent;
import org.popcraft.chunky.api.event.task.GenerationProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WorldPreloader {
    private static final Logger LOGGER = LoggerFactory.getLogger("Visor World Preloader");
    private static final String WORLD = "minecraft:overworld";
    private static final double RADIUS = 250.0;
    private static final AtomicBoolean NEW_WORLD_PENDING = new AtomicBoolean();
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    private static volatile float progress;
    private static volatile long chunks;
    private static volatile long hours;
    private static volatile long minutes;
    private static volatile long seconds;
    private static volatile double rate;
    private static volatile Path pendingMarker;

    private WorldPreloader() {
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(WorldPreloader::prepare);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ACTIVE.set(false));
    }

    public static void markNewWorld() {
        NEW_WORLD_PENDING.set(true);
    }

    private static void prepare(MinecraftServer server) {
        boolean newWorld = NEW_WORLD_PENDING.getAndSet(false);
        Path marker = server.getWorldPath(LevelResource.ROOT).resolve(".visor-chunky-preload-250.pending");
        if ((!newWorld && !Files.exists(marker)) || !FabricLoader.getInstance().isModLoaded("chunky")) {
            return;
        }
        pendingMarker = marker;
        server.execute(() -> start(server, newWorld));
    }

    private static void start(MinecraftServer server, boolean newWorld) {
        Chunky chunky = ChunkyProvider.get();
        if (chunky == null) {
            LOGGER.error("Chunky was loaded but its API was unavailable");
            return;
        }

        try {
            if (newWorld) {
                Files.writeString(pendingMarker, "radius=250");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create the Chunky preload marker", e);
            return;
        }

        progress = 0.0f;
        chunks = 0L;
        hours = 0L;
        minutes = 0L;
        seconds = 0L;
        rate = 0.0;
        ACTIVE.set(true);

        ChunkyAPI api = chunky.getApi();
        api.onGenerationProgress(WorldPreloader::onProgress);
        api.onGenerationComplete(WorldPreloader::onComplete);

        boolean started = !newWorld && api.continueTask(WORLD);
        if (!started) {
            BlockPos spawn = server.overworld().getSharedSpawnPos();
            started = api.startTask(WORLD, "circle", spawn.getX(), spawn.getZ(), RADIUS, RADIUS, "concentric");
        }

        if (!started) {
            ACTIVE.set(false);
            LOGGER.error("Automatic 250-block Chunky preload failed to start");
            return;
        }
        LOGGER.info("Automatic 250-block Chunky preload started");
    }

    private static void onProgress(GenerationProgressEvent event) {
        if (!WORLD.equals(event.world())) {
            return;
        }
        progress = event.progress();
        chunks = event.chunks();
        hours = event.hours();
        minutes = event.minutes();
        seconds = event.seconds();
        rate = event.rate();
    }

    private static void onComplete(GenerationCompleteEvent event) {
        if (!WORLD.equals(event.world())) {
            return;
        }
        try {
            Files.deleteIfExists(pendingMarker);
        } catch (IOException e) {
            LOGGER.warn("Failed to remove the Chunky preload marker", e);
        }
        progress = 1.0f;
        ACTIVE.set(false);
        LOGGER.info("Automatic 250-block Chunky preload completed after {} chunks", chunks);
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }

    public static float getProgress() {
        return progress;
    }

    public static long getChunks() {
        return chunks;
    }

    public static long getHours() {
        return hours;
    }

    public static long getMinutes() {
        return minutes;
    }

    public static long getSeconds() {
        return seconds;
    }

    public static double getRate() {
        return rate;
    }
}
