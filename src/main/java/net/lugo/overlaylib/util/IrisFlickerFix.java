package net.lugo.overlaylib.util;

import org.joml.Matrix4f;

public final class IrisFlickerFix {
    private static final IrisFlickerFix INSTANCE = new IrisFlickerFix();

    private static final float EPSILON = 1E-4f;

    private float anchorDistance = 64f;
    private float anchorOffset = 3E-2f;
    private float maxOffset = .25f;

    private float depthScale;
    private float targetMargin;
    private float offsetScale;

    private IrisFlickerFix() {
        recompute();
    }

    public static IrisFlickerFix getInstance() {
        return INSTANCE;
    }

    public float offset(float viewDistance) {
        if (viewDistance <= 0f) {
            return 0f;
        }
        float raw = offsetScale * viewDistance * viewDistance;
        return Math.min(raw, maxOffset);
    }

    public boolean refresh(Matrix4f projection) {
        float newDepthScale = depthScaleOf(projection);
        if (Float.isFinite(newDepthScale)
                && newDepthScale > 0f
                && Math.abs(newDepthScale - depthScale) <= EPSILON) {
            return false;
        }
        depthScale = newDepthScale;
        recompute();
        return true;
    }

    public float getAnchorDistance() {
        return anchorDistance;
    }

    public void setAnchorDistance(float anchorDistance) {
        this.anchorDistance = anchorDistance;
        recompute();
    }

    public float getAnchorOffset() {
        return anchorOffset;
    }

    public void setAnchorOffset(float anchorOffset) {
        this.anchorOffset = anchorOffset;
        recompute();
    }

    public float getMaxOffset() {
        return maxOffset;
    }

    public void setMaxOffset(float maxOffset) {
        this.maxOffset = maxOffset;
    }

    public float getDepthScale() {
        return depthScale;
    }

    public float getTargetMargin() {
        return targetMargin;
    }

    public float getOffsetScale() {
        return offsetScale;
    }

    private void recompute() {
        float anchorDistanceSq = anchorDistance * anchorDistance;
        if (Float.isFinite(depthScale) && depthScale > 0f) {
            targetMargin = anchorOffset * depthScale / anchorDistanceSq;
            offsetScale = targetMargin / depthScale;
        } else {
            targetMargin = Float.NaN;
            offsetScale = anchorOffset / anchorDistanceSq;
        }
    }

    private static float depthScaleOf(Matrix4f projection) {
        if (projection == null) {
            return Float.NaN;
        }
        float c = projection.m22();
        float e = projection.m32();
        if (!Float.isFinite(c) || !Float.isFinite(e) || e <= 0f || c + 1f == 0f || c - 1f == 0f) {
            return Float.NaN;
        }
        float near = e / (c + 1f);
        float far = e / (c - 1f);
        if (!(far > near)) {
            far = e / c;
        }
        if (!(far > near)) {
            return Float.NaN;
        }
        return far * near / (far - near);
    }
}
