package net.dvmn2.tablegamesplugin.util;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Builds display-entity {@link Transformation}s from yaw, pitch and scale.
 * <p>
 * Displays with {@code billboard = FIXED} do not face the player, so their orientation is baked
 * into the rotation quaternion instead of the entity's own yaw and pitch.
 */
public final class TransformUtil {

    private static final float FULL_TURN_DEGREES = 360f;

    private TransformUtil() {
    }

    /** A transformation that lays a display flat, rotated by {@code yawDegrees} and tilted by {@code pitchDegrees}. */
    public static Transformation flatTransformation(float yawDegrees, float pitchDegrees, Vector3f scale) {
        Quaternionf rotation = new Quaternionf()
                .rotateY((float) Math.toRadians(-normalizeYaw(yawDegrees)))
                .rotateX((float) Math.toRadians(pitchDegrees));
        return new Transformation(new Vector3f(0f, 0f, 0f), rotation, scale, new Quaternionf());
    }

    /** Maps any yaw to the range {@code [0, 360)}. */
    public static float normalizeYaw(float yaw) {
        float result = yaw % FULL_TURN_DEGREES;
        if (result < 0f) {
            result += FULL_TURN_DEGREES;
        }
        return result;
    }
}
