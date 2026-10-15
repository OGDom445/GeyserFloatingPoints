package oxy.geyser.fp.util;

import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Handles the 26.2 position packet and the 26.3 stepped-position packet. */
public final class EntityPositionAccess {
    private static final Method POSITION;
    private static final Method WITH_POSITION;
    private static final Method STEPS;
    private static final Method WITH_STEPS;
    private static final Method STEP_POSITION;
    private static final Method STEP_TICKS;
    private static final Constructor<?> STEP;

    static {
        try {
            Class<?> packet = ClientboundEntityPositionSyncPacket.class;
            Method position;
            Method withPosition;
            Method steps = null, withSteps = null, stepPosition = null, stepTicks = null;
            Constructor<?> step = null;
            try {
                position = packet.getMethod("getEndPosition");
                withPosition = packet.getMethod("withEndPosition", Vector3d.class);
                steps = packet.getMethod("getSteps");
                withSteps = packet.getMethod("withSteps", List.class);
                Class<?> stepClass = Class.forName(packet.getName() + "$PositionStep", false, packet.getClassLoader());
                stepPosition = stepClass.getMethod("position");
                stepTicks = stepClass.getMethod("tickOffset");
                step = stepClass.getConstructor(Vector3d.class, int.class);
            } catch (NoSuchMethodException legacy) {
                position = packet.getMethod("getPosition");
                withPosition = packet.getMethod("withPosition", Vector3d.class);
            }
            POSITION = position;
            WITH_POSITION = withPosition;
            STEPS = steps;
            WITH_STEPS = withSteps;
            STEP_POSITION = stepPosition;
            STEP_TICKS = stepTicks;
            STEP = step;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported Geyser entity position API", e);
        }
    }

    private EntityPositionAccess() {
    }

    public static void validate() {
    }

    public static ClientboundEntityPositionSyncPacket offset(ClientboundEntityPositionSyncPacket packet, Vector3d offset) {
        try {
            Vector3d position = (Vector3d) POSITION.invoke(packet);
            // In 26.3 the fields are alternatives: stepped packets have no endPosition,
            // and non-stepped packets have no steps. Preserve the inactive null field.
            Object result = position == null ? packet : WITH_POSITION.invoke(packet, position.sub(offset));
            if (STEPS != null) {
                List<?> steps = (List<?>) STEPS.invoke(packet);
                if (steps != null) {
                    List<Object> shifted = new ArrayList<>(steps.size());
                    for (Object step : steps) {
                        Vector3d stepPosition = (Vector3d) STEP_POSITION.invoke(step);
                        shifted.add(STEP.newInstance(stepPosition.sub(offset), STEP_TICKS.invoke(step)));
                    }
                    result = WITH_STEPS.invoke(result, shifted);
                }
            }
            return (ClientboundEntityPositionSyncPacket) result;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not offset entity position packet", e);
        }
    }
}
