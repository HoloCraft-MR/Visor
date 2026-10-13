package org.vmstudio.visor.core.client.settings;

import org.vmstudio.visor.api.client.settings.VROptionCategory;
import org.vmstudio.visor.core.client.settings.options.OptionBehaviour;

/**
 * A settings widget: the built-in {@link VROptionWidgetType}s, or a
 * {@link VRCustomOptionWidget} registered by an addon through {@link VRSettingsRegistry}
 */
public interface VROptionWidget {

    /**
     * @return full option key (category + key), null for widgets without an option
     */
    String getKey();

    VROptionCategory getCategory();

    OptionBehaviour getBehaviour();
}
