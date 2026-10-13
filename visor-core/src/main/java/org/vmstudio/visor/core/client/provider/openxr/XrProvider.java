package org.vmstudio.visor.core.client.provider.openxr;

import org.vmstudio.visor.api.client.VisorClientEvents;

import lombok.Getter;
import me.phoenixra.atumvr.api.AtumVRLogger;
import me.phoenixra.atumvr.api.rendering.AtumVRRenderer;
import me.phoenixra.atumvr.core.XRProvider;
import me.phoenixra.atumvr.core.XRState;
import me.phoenixra.atumvr.core.enums.XRGraphicsApi;
import me.phoenixra.atumvr.core.enums.XRSessionState;
import me.phoenixra.atumvr.core.session.platform.XRPlatform;
import org.vmstudio.visor.api.client.settings.VRClientSettings;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.VisorClientImpl;
import org.vmstudio.visor.core.client.provider.openxr.render.XrRenderer;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.openxr.FBCompositionLayerSettings;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

import static org.vmstudio.visor.core.client.VisorClientImpl.MC;

public class XrProvider extends XRProvider {

    @Getter
    private XRSessionState sessionState = XRSessionState.UNKNOWN;

    public XrProvider(@NotNull String appName, @NotNull AtumVRLogger logger) {
        super(appName, logger);

        XRPlatform.setAndroidBridge(new QuestAndroidXRBridge());

        ClientContext.rawPoseHandler = new XrRawPoseHandler(this);
        ClientContext.inputProvider = inputHandler;
    }

    @Override
    public float getPreferredRefreshRate() {
        if (!XRPlatform.isAndroid()) {
            return super.getPreferredRefreshRate();
        }
        return QuestVLoader.isQuest3Family() ? 120.0F : 72.0F;
    }

    @Override
    public void initializeVR() throws Throwable {

        super.initializeVR();

        ClientContext.rawPoseHandler.getTrackersData().setTracking(
                !getInputHandler().getTrackerProviders().isEmpty()
        );
        ClientContext.rawPoseHandler.getHandsData().setTracking(
                getInputHandler().getHandsProvider() != null
        );

        ClientContext.settingsManager.loadOptions();

        VisorClientImpl.LOGGER.info("OpenXR initialized");
    }

    @Override
    public void startFrame() {
        super.startFrame();
        ClientContext.rawPoseHandler.updatePose();
    }

    @Override
    public @Nullable XRGraphicsApi getGraphicsApiPreference() {
        return VRClientSettings.getGraphicsApi().toPreference();
    }

    @Override
    public @NotNull XRState createStateHandler() {
        return new XRState(this);
    }

    @Override
    public @NotNull XrInputHandler createInputHandler() {
        return new XrInputHandler(this);
    }

    /** Base extensions + XR_FB_composition_layer_settings (quad layers); enabled only if the runtime has it */
    @Override
    public @NotNull List<String> getXRAppExtensions() {
        List<String> extensions = new ArrayList<>(super.getXRAppExtensions());
        extensions.add(FBCompositionLayerSettings.XR_FB_COMPOSITION_LAYER_SETTINGS_EXTENSION_NAME);
        return extensions;
    }

    @Override
    public @NotNull AtumVRRenderer createRenderer() {
        return new XrRenderer(this);
    }

    @Override
    public void onStateChanged(XRSessionState state) {
        sessionState = state;
        VisorClientEvents.fireXrSessionStateChanged(state);
    }

    @Override
    protected void onReferenceSpaceChangePending(int referenceSpaceType, long changeTime, boolean poseValid) {
        super.onReferenceSpaceChangePending(referenceSpaceType, changeTime, poseValid);
        VisorClientEvents.fireReferenceSpaceChanged();
    }

    @Override
    public @NotNull XrInputHandler getInputHandler() {
        return (XrInputHandler) super.getInputHandler();
    }
}
