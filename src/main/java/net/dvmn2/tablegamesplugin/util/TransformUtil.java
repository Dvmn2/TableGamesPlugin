package net.dvmn2.tablegamesplugin.util;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Builds display-entity {@link Transformation}s from simple yaw/pitch/scale inputs.
 * <p>
 * Display entities using {@code billboard = FIXED} do not automatically face the
 * player, so their visual orientation must be baked into the transformation's
 * rotation quaternion instead of the entity's own yaw/pitch fields.
 */
public final class TransformUtil {

    private TransformUtil() {
    }

    public static Transformation flatTransformation(float yawDegrees, float pitchDegrees, Vector3f scale) {
        Quaternionf rotation = new Quaternionf()
                .rotateY((float) Math.toRadians(-normalizeYaw(yawDegrees)))
                .rotateX((float) Math.toRadians(pitchDegrees));
        return new Transformation(new Vector3f(0f, 0f, 0f), rotation, scale, new Quaternionf());
    }

    public static float normalizeYaw(float yaw) {
        float result = yaw % 360f;
        if (result < 0f) {
            result += 360f;
        }
        return result;
    }
}
