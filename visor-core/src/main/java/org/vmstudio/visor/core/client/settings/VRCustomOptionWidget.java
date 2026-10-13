package org.vmstudio.visor.core.client.settings;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.vmstudio.visor.api.client.settings.VROptionCategory;
import org.vmstudio.visor.core.client.settings.options.OptionBehaviour;
import org.vmstudio.visor.core.client.settings.options.OptionBehaviourFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * Option widget defined outside {@link VROptionWidgetType}, e.g. by an addon.
 * The key is set when the option is registered in {@link VRSettingsRegistry}.
 * The behaviour is created on first use, so it can read the key
 */
public class VRCustomOptionWidget implements VROptionWidget {

    @Getter
    @Setter
    private String key;

    @Getter
    private final VROptionCategory category;

    private final Function<VROptionWidget, OptionBehaviour> behaviourProvider;
    private OptionBehaviour behaviour;

    public VRCustomOptionWidget(@NotNull VROptionCategory category,
                                @NotNull Function<VROptionWidget, OptionBehaviour> behaviourProvider) {
        this.category = category;
        this.behaviourProvider = behaviourProvider;
    }

    @Override
    public OptionBehaviour getBehaviour() {
        if (behaviour == null) {
            behaviour = Objects.requireNonNullElseGet(
                    behaviourProvider.apply(this),
                    () -> OptionBehaviourFactory.simple(this).build()
            );
        }
        return behaviour;
    }

    @Override
    public String toString() {
        return "VRCustomOptionWidget[" + key + "]";
    }
}
