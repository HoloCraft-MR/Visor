package org.vmstudio.visor.loader.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.vmstudio.visor.api.compatibility.mcversion.McVersionClientUtils;
import org.vmstudio.visor.api.compatibility.mcversion.gui.McGuiUtils;
import org.vmstudio.visor.api.compatibility.mcversion.gui.McScreen;

import java.util.Locale;

public final class WorldPreloaderScreen extends McScreen {
    public WorldPreloaderScreen() {
        super(Component.literal("Pre-generating world"));
    }

    @Override
    public void tick() {
        if (!WorldPreloader.isActive()) {
            McVersionClientUtils.setScreen(null);
        }
    }

    @Override
    protected void renderContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float rawProgress = WorldPreloader.getProgress();
        float normalized = rawProgress > 1.0f ? rawProgress / 100.0f : rawProgress;
        normalized = Math.max(0.0f, Math.min(1.0f, normalized));
        int centerX = width / 2;
        int centerY = height / 2;
        int barWidth = Math.min(320, width - 80);
        int barLeft = centerX - barWidth / 2;
        int barTop = centerY + 18;

        McGuiUtils.drawCenteredString(graphics, font, title, centerX, centerY - 46, 0xFFFFFFFF);
        graphics.fill(barLeft - 1, barTop - 1, barLeft + barWidth + 1, barTop + 11, 0xFF606060);
        graphics.fill(barLeft, barTop, barLeft + Math.round(barWidth * normalized), barTop + 10, 0xFF55FF55);

        String progressText = String.format(Locale.ROOT, "%.1f%%  |  %,d chunks  |  %.1f chunks/s", normalized * 100.0f, WorldPreloader.getChunks(), WorldPreloader.getRate());
        String etaText = String.format(Locale.ROOT, "Estimated time remaining: %02d:%02d:%02d", WorldPreloader.getHours(), WorldPreloader.getMinutes(), WorldPreloader.getSeconds());
        McGuiUtils.drawCenteredString(graphics, font, progressText, centerX, barTop + 20, 0xFFFFFFFF);
        McGuiUtils.drawCenteredString(graphics, font, etaText, centerX, barTop + 38, 0xFFBFBFBF);
        McGuiUtils.drawCenteredString(graphics, font, "The world will open automatically when preloading finishes", centerX, barTop + 62, 0xFFA0A0A0);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
