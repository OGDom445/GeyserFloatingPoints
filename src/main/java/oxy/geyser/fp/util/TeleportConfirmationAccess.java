// Modified by OGDom445 on 2026-10-15: unofficial teleport-offset fix. GNU GPL v3; see LICENSE.
package oxy.geyser.fp.util;

import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;

import java.lang.reflect.Method;

/** Restores Java world coordinates on new confirmations, preserving legacy ID-only packets. */
public final class TeleportConfirmationAccess {
    private static final Method X, Z, WITH_X, WITH_Z;
    static {
        try {
            Class<?> type=ServerboundAcceptTeleportationPacket.class;
            Method x;
            try { x=type.getMethod("getX"); }
            catch(NoSuchMethodException legacy) {
                // Only treat the known ID-only packet as legacy. Fail fast on
                // an unknown future packet rather than silently sending bad coordinates.
                type.getConstructor(int.class);
                x=null;
            }
            X=x;
            Z=x==null?null:type.getMethod("getZ");
            WITH_X=x==null?null:type.getMethod("withX",double.class);
            WITH_Z=x==null?null:type.getMethod("withZ",double.class);
        } catch(ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported Geyser teleport confirmation API",e);
        }
    }
    public static void validate() {}

    public static ServerboundAcceptTeleportationPacket restore(ServerboundAcceptTeleportationPacket packet,Vector3i offset) {
        if(X==null || offset.equals(Vector3i.ZERO)) return packet;
        try {
            // The extension never offsets Y. Copy methods retain the teleport ID,
            // precise Y, yaw, pitch and any other packet state without mutating it.
            Object translated=WITH_X.invoke(packet,(double)X.invoke(packet)+offset.getX());
            return (ServerboundAcceptTeleportationPacket)WITH_Z.invoke(translated,(double)Z.invoke(packet)+offset.getZ());
        } catch(ReflectiveOperationException e) {
            throw new IllegalStateException("Could not restore teleport confirmation coordinates",e);
        }
    }
    private TeleportConfirmationAccess() {}
}
