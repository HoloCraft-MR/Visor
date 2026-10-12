# Quest (Android) port

This branch carries coah's Meta Quest port of Visor (coah80/Visor `a0275017`, `25740234`, `32c80265`) rebased onto the multi-version upstream. On Quest, Visor runs inside a Pojav-style Android launcher (QuestCraft) that loads Fabric 1.20.1 and provides `libvloader.so`.

## AtumVR dependency

The Quest code needs the Android OpenXR platform layer (`me.phoenixra.atumvr.core.session.platform.XRPlatform`, `AndroidXRBridge`). It lives in the AtumVR `quest-port` branch, not in the `libs/atumvr-*-#26.jar` that upstream ships.

coah's prebuilt `atumvr-*-quest.jar` and `lwjgl-openxr-quest-3.3.3.jar` are **not** committed. Visor instead resolves AtumVR from Maven Local:

1. Build and publish AtumVR (branch `quest-port`):

   ```sh
   cd ../AtumVR
   ./gradlew publishToMavenLocal
   ```

   This publishes `me.phoenixra:atumvr-api` and `me.phoenixra:atumvr-core`, version `1.14.0beta-snapshot#14`, to `~/.m2/repository`.

2. `stonecutter.properties.toml` selects it:

   ```toml
   atumvr_source  = "mavenLocal"
   atumvr_version = "1.14.0beta-snapshot#14"
   ```

   `atumvr_source = "mavenLocal"` (in `buildSrc/.../visor.minecraft.gradle`) maps to `me.phoenixra:atumvr-<module>:<atumvr_version>`. Maven Local is only consulted for the `me.phoenixra` group.

   - Set `atumvr_source = "local"` and `atumvr_version = "1.14.0beta-snapshot#26"` to go back to the upstream jars in `libs/`. Those jars lack `XRPlatform`, so the Quest classes will not compile against them.
   - If AtumVR's `version.properties` build number changes, update `atumvr_version` to match.

lwjgl-openxr stays on the normal Maven artifact (`org.lwjgl:lwjgl-openxr:3.3.6` for 1.20.1). AtumVR's quest-port does not need QuestCraft's custom LWJGL build; it hand-writes the few Android/GLES structs. No LWJGL jar is vendored.

## Building

```sh
sh gradlew --configure-on-demand :visor-fabric:1.20.1:buildAndCollect
# -> build/libs/Visor-<version>+mc1.20.1-fabric.jar
```

`--configure-on-demand` keeps Gradle from setting up Loom for every Minecraft version in the tree, which needs a lot of disk.

## Runtime pieces

- `QuestVLoader` / `QuestAndroidXRBridge` (visor-core) bind to the launcher's `libvloader.so` for the EGL display/context/config, JavaVM, Activity and device model. They are only touched on Android: `XRPlatform.isAndroid()` checks for `os.version` containing `Android`.
- Android-only behaviour: no desktop mirror pass, a 1×1 mirror target, eye passes only, 72/120 Hz refresh by device model, and the GL sRGB toggles.
- Fabric only: `WorldPreloader` pre-generates a 250-block radius with [Chunky](https://github.com/pop4959/Chunky) (GPLv3, optional) after a new world is created. Chunky is a compile-only dependency (`maven.modrinth:chunky:NHWYq9at` = Chunky 1.3.146 Fabric) and is not bundled. The preloader does nothing unless the `chunky` mod is installed.
