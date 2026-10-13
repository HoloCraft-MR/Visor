package org.vmstudio.visor.core.client.settings;

import me.phoenixra.atumconfig.api.config.Config;
import org.jetbrains.annotations.ApiStatus;
import org.vmstudio.visor.api.client.settings.VRClientSettings;
import org.vmstudio.visor.api.client.settings.VROptionCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vmstudio.visor.core.client.gui.screens.settings.OptionWidgetEntry;
import org.vmstudio.visor.core.client.gui.screens.settings.VROptionsSet;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Addon settings: options stored in Visor's settings file (like {@link VRClientSettings} fields)
 * and widgets added to Visor's settings pages.
 * <p>
 *     Options must be registered before the Visor client starts
 *     (e.g. in {@code VisorAddon#onAddonRegister}).
 * </p>
 */
public final class VRSettingsRegistry {

    @ApiStatus.Internal
    public record Option(@NotNull Field field,
                         @NotNull VROptionCategory category,
                         @NotNull String key,
                         @Nullable VROptionWidget widget,
                         boolean excludeForcedChange) { }

    private record PageEntries(@NotNull Class<? extends VROptionsSet> page,
                               @NotNull BiConsumer<VROptionsSet, List<OptionWidgetEntry>> contributor) { }

    private static final List<Option> OPTIONS = new CopyOnWriteArrayList<>();
    private static final List<PageEntries> PAGE_ENTRIES = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Config>> LOAD_LISTENERS = new CopyOnWriteArrayList<>();

    private VRSettingsRegistry() {
        throw new UnsupportedOperationException("This is an utility class and cannot be instantiated");
    }

    /**
     * Register a static field as a settings option, saved as {@code <category key>.<key>}
     *
     * @param holder class declaring the static field
     * @param fieldName the field
     * @param category option category
     * @param key option key inside the category
     * @param widget its widget (null: stored only, like Visor's internal values)
     * @param excludeForcedChange not changed by presets / "load defaults"
     */
    public static void registerOption(@NotNull Class<?> holder,
                                      @NotNull String fieldName,
                                      @NotNull VROptionCategory category,
                                      @NotNull String key,
                                      @Nullable VROptionWidget widget,
                                      boolean excludeForcedChange) {
        try {
            Field field = holder.getDeclaredField(fieldName);
            if (!Modifier.isStatic(field.getModifiers())) {
                throw new IllegalArgumentException("VR option field must be static: " + holder.getName() + "." + fieldName);
            }
            field.setAccessible(true);
            OPTIONS.add(new Option(field, category, key, widget, excludeForcedChange));
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("No VR option field " + holder.getName() + "." + fieldName, e);
        }
    }

    /**
     * Add widgets to a settings page, each time the page builds its widgets
     *
     * @param page settings page class (e.g. VRSettingsRendering)
     * @param contributor adds entries (owner = the page instance)
     */
    public static void registerPageEntries(@NotNull Class<? extends VROptionsSet> page,
                                           @NotNull BiConsumer<VROptionsSet, List<OptionWidgetEntry>> contributor) {
        PAGE_ENTRIES.add(new PageEntries(page, contributor));
    }

    /**
     * Called once at client start after the settings file was loaded and before it is
     * re-saved (migrations of older settings files)
     */
    public static void addLoadListener(@NotNull Consumer<Config> listener) {
        LOAD_LISTENERS.add(listener);
    }

    @ApiStatus.Internal
    public static List<Option> getOptions() {
        return OPTIONS;
    }

    @ApiStatus.Internal
    public static OptionWidgetEntry[] withPageEntries(@NotNull VROptionsSet page, OptionWidgetEntry[] entries) {
        List<OptionWidgetEntry> added = new ArrayList<>();
        for (PageEntries pageEntries : PAGE_ENTRIES) {
            if (pageEntries.page() == page.getClass()) {
                pageEntries.contributor().accept(page, added);
            }
        }
        if (added.isEmpty()) {
            return entries;
        }
        List<OptionWidgetEntry> all = new ArrayList<>();
        if (entries != null) {
            all.addAll(List.of(entries));
        }
        all.addAll(added);
        return all.toArray(new OptionWidgetEntry[0]);
    }

    @ApiStatus.Internal
    public static void fireLoaded(@NotNull Config config) {
        for (Consumer<Config> listener : LOAD_LISTENERS) {
            listener.accept(config);
        }
    }
}
