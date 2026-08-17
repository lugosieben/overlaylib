package net.lugo.overlaylib.renderers;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.lugo.overlaylib.util.OverlayVertexHelper;
import net.lugo.overlaylib.util.UVRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class CamFacingTextureOverlayRenderer extends TextureOverlayRenderer {
    private static final Minecraft MC = Minecraft.getInstance();

    private static final float EPSILON = 1E-3f;
    private UVRotation uvRotation = UVRotation.NONE;

    public CamFacingTextureOverlayRenderer(Identifier texture) {
        this(texture, true);
    }

    public CamFacingTextureOverlayRenderer(Identifier texture, boolean doIrisFlickerFix) {
        super(texture, doIrisFlickerFix);
    }

    public final void startBatch(LevelRenderContext context) {
        super.startBatch(context);
        if (MC.getCameraEntity() != null) {
            uvRotation = UVRotation.of(MC.getCameraEntity().getDirection());
        }
    }

    @Override
    public long getFrameStateToken() {
        return uvRotation == null ? 0L : uvRotation.ordinal();
    }

    @Override
    protected void addVertices(float x, float y, float z, OverlayRendererBlockData data) {
        OverlayVertexHelper.texturedSquare(
                buffer,
                OverlayVertexHelper.FixedAxis.Y, y + 1f + EPSILON,
                x, z,
                data.color(),
                data.textureSection(),
                uvRotation
        );
    }
}
