package net.lugo.overlaylib.renderers;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.lugo.overlaylib.OverlayRenderer;
import net.lugo.overlaylib.util.RenderPipelines;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.lugo.overlaylib.util.TextureSection;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

public class SimpleTextureOverlayRenderer extends OverlayRenderer {

    public SimpleTextureOverlayRenderer(Identifier texture, boolean doIrisFlickerFix) {
        super(RenderPipelines.POSITION_TEX_COLOR_FOG, texture, doIrisFlickerFix);
    }

    @Override
    protected void onStartBatch() {

    }

    @Override
    protected void addVertices(VertexConsumer buffer, Matrix4f positionMatrix, OverlayRendererBlockData data) {
        TextureSection textureSection = data.textureSection().orElse(TextureSection.SINGULAR);
        positionMatrix.translate(0f, 1E-3f, 0f);

        buffer.addVertex(positionMatrix, 0, 1, 0).setColor(data.r(), data.g(), data.b(), 1f).setUv(textureSection.uStart(), textureSection.vStart());
        buffer.addVertex(positionMatrix, 0, 1, 1).setColor(data.r(), data.g(), data.b(), 1f).setUv(textureSection.uStart(), textureSection.vEnd());
        buffer.addVertex(positionMatrix, 1, 1, 1).setColor(data.r(), data.g(), data.b(), 1f).setUv(textureSection.uEnd(), textureSection.vEnd());
        buffer.addVertex(positionMatrix, 1, 1, 0).setColor(data.r(), data.g(), data.b(), 1f).setUv(textureSection.uEnd(), textureSection.vStart());
    }

    @Override
    protected void onEndBatch() {

    }
}
