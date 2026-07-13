package org.vmstudio.visor.core.client.provider.openxr;

public final class QuestVLoader {
    static {
        System.loadLibrary("vloader");
    }

    private QuestVLoader() {
    }

    public static native long getEGLDisplay();
    public static native long getEGLContext();
    public static native long getEGLConfig();
    public static native long getDalvikVM();
    public static native long getDalvikActivity();
    public static native void setupAndroid();
}
