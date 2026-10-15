# GeyserFloatingPoints 2.1.1-patched

Unofficial compatibility patch prepared October 15, 2026 from oryxel1/GeyserFloatingPoints, commit 9f242a3 (GPL-3.0).
Original project: https://github.com/oryxel1/GeyserFloatingPoints

## Install

1. Fully stop your Minecraft server.
2. Save your existing GeyserFloatingPoints JAR outside the server's plugins and extensions directories.
3. Put geyserfloatingpoints-2.1.1-patched.jar into plugins/Geyser-Spigot/extensions/ (use the actual extensions folder if your Geyser data folder has a different name).
4. Keep exactly one GeyserFloatingPoints JAR installed. Keep your existing extension config.
5. Start the server normally. Do not use /reload.
6. Try a Bedrock join, then movement, teleports beyond +/-3000 X/Z, block breaking/placing, signs, vehicles, and dimension changes.

This is a Geyser extension; do not put it directly in plugins/.
Keep Boar disabled during the first test: its null-player-manager error in the supplied log is a separate issue this JAR does not patch.

## Versions and limits

The same packaged JAR passed binary-link and packet regression checks with:

- Geyser-Spigot 2.11.3 build 1254 (26.2 era).
- Geyser-Spigot 2.12.0 build 1258 (current 26.3 support).
- Java 25 runtime, Java 21 class-file target, and Netty 4.2.15.Final.

Geyser currently lists Java 26.3 support. For a Paper 26.2 backend with a Geyser build that emulates 26.3, install a ViaVersion build that supports 26.3 clients. Your backend must accept the Java protocol emulated by your installed Geyser build.
Official requirements: https://geysermc.org/wiki/geyser/supported-versions/

No live Paper server or authenticated Bedrock client was available for an end-to-end join/play test. These checks establish compatibility of the tested APIs and packet operations, not a guarantee of every gameplay feature. Later Geyser internal API changes can still need another patch. Existing upstream limitations of this coordinate-spoofing extension remain.

## 2.1.1 correction

The first patch incorrectly assumed both fields of the 26.3 entity position packet were present. Actual stepped packets have a null endPosition; non-stepped packets have a null steps list. This caused the reported join-time NullPointerException. Version 2.1.1 preserves the inactive null field and translates the populated field. Regression tests now encode and decode both valid packet forms through the actual protocol codec, checking translated coordinates, both steps, timing, flags, and original-packet immutability. All checks passed on both listed Geyser releases. This replaces 2.1-patched.

## Changes

- Replaces direct getRegistryCache() binary linkage with runtime resolution of the legacy registry API or the new javaRegistries() API, including moved registry classes.
- Handles both the 26.2 entity position packet and the 26.3 stepped packet, translating intermediate positions while preserving velocity/timing and packet flags.
- Uses packet copy methods for sign and jigsaw positions to avoid changed sign field types and Geyser-Spigot's relocated Adventure Key types.
- Propagates movement-packet cancellation to the underlying sending event during coordinate recentering.
- Releases the temporary chunk-data buffer, including on parsing failures.
- Uses biome network ID 0 when initializing an empty section, instead of confusing a mapped Bedrock biome value with a Java network ID.
- Uses a concurrent session map and resolves compatibility methods before installing handlers.
- Produces Java 21 class files; updates Lombok so the build also works on JDK 25.
- Removes upstream publishing configuration from this local build. No upstream release was published.

## Build and checks

Build with JDK 21 or newer:

    ./gradlew.bat clean shadowJar

Output: build/libs/geyserfloatingpoints.jar
To compile against the previous Geyser core dependency:

    ./gradlew.bat clean shadowJar "-PgeyserVersion=2.11.3-SNAPSHOT"

Both dependency versions compiled successfully. The default is 2.12.0-SNAPSHOT; snapshot dependency contents can change over time.

Run reproducible checks in PowerShell with a JDK installed:

    ./tests/check.ps1

The script downloads two hash-verified production Geyser-Spigot JARs plus test dependencies, builds the extension, and checks 472 external method/field references in its packaged bytecode against each production JAR. Separate isolated class loaders prevent the compile-time core dependency from hiding incompatibilities.
Behavior checks cover positive/negative coordinate offsets, unchanged Y, preserved packet fields, zero offset, original packet immutability, 26.2 velocity, 26.3 intermediate steps/timing, registry API resolution, and packet cancellation/forwarding.
The current registry accessor is also exercised with a session containing a test registry. Legacy registry data lookup is explicitly skipped because it requires a bootstrapped Geyser singleton; its method/field resolution is checked.

Production JAR SHA-256:

    2.11.3 b1254  9ebd45b5728a6ffa97a91766d7511c4292c84e6a226bfde0233d5a95b373a20e
    2.12.0 b1258  585eb449dbdaef09691433f84d29a5c557c3a27effc40c6d50b245725dc91b41

This source archive includes the complete modified source, Gradle wrapper, tests, and upstream license. No Geyser or Paper binaries are bundled in the extension.

