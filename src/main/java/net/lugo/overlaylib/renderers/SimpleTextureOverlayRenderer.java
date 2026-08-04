package net.lugo.overlaylib.renderers;

import net.lugo.overlaylib.OverlayRenderer;
import net.lugo.overlaylib.util.OverlayVertexHelper;
import net.lugo.overlaylib.util.RenderPipelines;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.lugo.overlaylib.util.UVRotation;
import net.minecraft.resources.Identifier;

public class SimpleTextureOverlayRenderer extends OverlayRenderer {
    private static final float EPSILON = 1E-3f;

    public SimpleTextureOverlayRenderer(Identifier texture) {
        super(RenderPipelines.POSITION_TEX_COLOR_FOG_TRIANGLES, texture);
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
                UVRotation.NONE
        );
    }
}
