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
        float r = data.r();
        float g = data.g();
        float b = data.b();
        float uStart = textureSection.uStart();
        float vStart = textureSection.vStart();
        float uEnd = textureSection.uEnd();
        float vEnd = textureSection.vEnd();

        // Emit two triangles for the top face so we can use TRIANGLES mode without quad sorting.
        buffer.addVertex(worldX, y, worldZ).setColor(r, g, b, 1f).setUv(uStart, vStart);
        buffer.addVertex(worldX, y, worldZ + 1f).setColor(r, g, b, 1f).setUv(uStart, vEnd);
        buffer.addVertex(worldX + 1f, y, worldZ + 1f).setColor(r, g, b, 1f).setUv(uEnd, vEnd);

        buffer.addVertex(worldX, y, worldZ).setColor(r, g, b, 1f).setUv(uStart, vStart);
        buffer.addVertex(worldX + 1f, y, worldZ + 1f).setColor(r, g, b, 1f).setUv(uEnd, vEnd);
        buffer.addVertex(worldX + 1f, y, worldZ).setColor(r, g, b, 1f).setUv(uEnd, vStart);
    }
}
