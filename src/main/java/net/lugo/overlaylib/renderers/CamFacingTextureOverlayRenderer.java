package net.lugo.overlaylib.renderers;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.lugo.overlaylib.OverlayLib;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.lugo.overlaylib.util.OverlayVertexHelper;
import net.lugo.overlaylib.util.UVRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class CamFacingTextureOverlayRenderer extends SimpleTextureOverlayRenderer {
    private static final Minecraft MC = Minecraft.getInstance();

    private static final float EPSILON = 1E-3f;
    private UVRotation uvRotation = UVRotation.NONE;

    public CamFacingTextureOverlayRenderer(Identifier texture, boolean doIrisFlickerFix) {
        super(texture, doIrisFlickerFix);
    }

    public final void startBatch(LevelRenderContext context) {
        super.startBatch(context);
        if (MC.getCameraEntity() != null) {
            uvRotation = UVRotation.of(MC.getCameraEntity().getDirection());
            OverlayLib.LOGGER.info("Camera direction: {}, UV rotation: {}", MC.getCameraEntity().getDirection(), uvRotation);
        }
    }

    @Override
    protected void addVertices(float worldX, float worldY, float worldZ, OverlayRendererBlockData data) {
        float y = worldY + 1f + EPSILON;

        OverlayVertexHelper.squareFromTriags(
                buffer,
                OverlayVertexHelper.FixedAxis.Y, y,
                worldX, worldZ,
                data.r(), data.g(), data.b(),
                data.textureSection().uStart(), data.textureSection().vStart(),
                data.textureSection().uEnd(), data.textureSection().vEnd(),
                uvRotation
        );
    }
}
