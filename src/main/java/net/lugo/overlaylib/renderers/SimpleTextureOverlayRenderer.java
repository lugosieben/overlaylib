package net.lugo.overlaylib.renderers;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.lugo.overlaylib.OverlayRenderer;
import net.lugo.overlaylib.util.RenderPipelines;
import net.minecraft.core.BlockPos;
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
    protected void addVertices(VertexConsumer buffer, Matrix4f positionMatrix, float r, float g, float b, float uStart, float uEnd, float vStart, float vEnd, BlockPos pos) {
        positionMatrix.translate(0f, 1E-3f, 0f);

        buffer.addVertex(positionMatrix, 0, 1, 0).setColor(r, g, b, 1f).setUv(uStart, vStart);
        buffer.addVertex(positionMatrix, 0, 1, 1).setColor(r, g, b, 1f).setUv(uStart, vEnd);
        buffer.addVertex(positionMatrix, 1, 1, 1).setColor(r, g, b, 1f).setUv(uEnd, vEnd);
        buffer.addVertex(positionMatrix, 1, 1, 0).setColor(r, g, b, 1f).setUv(uEnd, vStart);
    }

    @Override
    protected void onEndBatch() {

    }
}
