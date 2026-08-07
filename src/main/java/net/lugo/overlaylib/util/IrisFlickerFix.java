package net.lugo.overlaylib.util;

public final class IrisFlickerFix {
    private static final IrisFlickerFix INSTANCE = new IrisFlickerFix();

    private float anchorDistance = 64f;
    private float anchorOffset = 3E-2f;
    private float maxOffset = .25f;

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

    public float getOffsetScale() {
        return offsetScale;
    }

    private void recompute() {
        float anchorDistanceSq = anchorDistance * anchorDistance;
        offsetScale = anchorOffset / anchorDistanceSq;
    }
}
