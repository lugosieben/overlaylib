package net.lugo.overlaylib.renderers;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.lugo.overlaylib.OverlayRenderer;
import net.lugo.overlaylib.util.OverlayVertexHelper;
import net.lugo.overlaylib.util.RenderPipelines;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.lugo.overlaylib.util.TextureSection;
import net.minecraft.resources.Identifier;

public class SimpleTextureOverlayRenderer extends OverlayRenderer {
    private static final float EPSILON = 1E-3f;

    public SimpleTextureOverlayRenderer(Identifier texture, boolean doIrisFlickerFix) {
        super(RenderPipelines.POSITION_TEX_COLOR_FOG_TRIANGLES, texture, doIrisFlickerFix);
    }

    @Override
    protected void addVertices(VertexConsumer buffer, float worldX, float worldY, float worldZ, OverlayRendererBlockData data) {
        TextureSection textureSection = data.textureSection().orElse(TextureSection.SINGULAR);

        float y = worldY + 1f + EPSILON;

        OverlayVertexHelper.squareFromTriags(
                buffer,
                OverlayVertexHelper.FixedAxis.Y, y,
                worldX, worldZ,
                1f,
                data.r(), data.g(), data.b(),
                textureSection.uStart(), textureSection.vStart(),
                textureSection.uEnd(), textureSection.vEnd()
        );
    }
}
