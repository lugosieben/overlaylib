package net.lugo.overlaylib.renderers;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.lugo.overlaylib.OverlayRenderer;
import net.lugo.overlaylib.util.RenderPipelines;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.lugo.overlaylib.util.TextureSection;
import net.minecraft.resources.Identifier;

public class SimpleTextureOverlayRenderer extends OverlayRenderer {

    public SimpleTextureOverlayRenderer(Identifier texture, boolean doIrisFlickerFix) {
        super(RenderPipelines.POSITION_TEX_COLOR_FOG, texture, doIrisFlickerFix);
    }

    @Override
    protected void addVertices(VertexConsumer buffer, float worldX, float worldY, float worldZ, OverlayRendererBlockData data) {
        TextureSection textureSection = data.textureSection().orElse(TextureSection.SINGULAR);
        float y = worldY + 1f + 1E-3f;

        buffer.addVertex(worldX, y, worldZ).setColor(data.r(), data.g(), data.b(), 1f).setUv(textureSection.uStart(), textureSection.vStart());
        buffer.addVertex(worldX, y, worldZ + 1f).setColor(data.r(), data.g(), data.b(), 1f).setUv(textureSection.uStart(), textureSection.vEnd());
        buffer.addVertex(worldX + 1f, y, worldZ + 1f).setColor(data.r(), data.g(), data.b(), 1f).setUv(textureSection.uEnd(), textureSection.vEnd());
        buffer.addVertex(worldX + 1f, y, worldZ).setColor(data.r(), data.g(), data.b(), 1f).setUv(textureSection.uEnd(), textureSection.vStart());
    }
}
