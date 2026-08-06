package net.lugo.overlaylib.renderers;

import net.lugo.overlaylib.CachedMeshOverlayRenderer;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.lugo.overlaylib.util.OverlayVertexHelper;
import net.lugo.overlaylib.util.RenderPipelines;
import net.lugo.overlaylib.util.UVRotation;
import net.minecraft.resources.Identifier;

public class SimpleTextureOverlayRenderer extends CachedMeshOverlayRenderer {
    private static final float EPSILON = 1E-3f;

    public SimpleTextureOverlayRenderer(Identifier texture) {
        super(RenderPipelines.POSITION_TEX_COLOR_FOG_TRIANGLES, texture);
    }

    @Override
    protected void addVertices(float x, float y, float z, OverlayRendererBlockData data) {
        OverlayVertexHelper.texturedSquareAtY(
                buffer,
                x, y + 1f + EPSILON, z,
                data.r(), data.g(), data.b(),
                data.textureSection(),
                UVRotation.NONE
        );
    }
}
