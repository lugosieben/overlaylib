package net.lugo.overlaylib.renderers;

import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.lugo.overlaylib.util.OverlayVertexHelper;
import net.lugo.overlaylib.util.RenderPipelines;
import net.lugo.overlaylib.util.UVRotation;
import net.minecraft.resources.Identifier;

public class TextureOverlayRenderer extends CachedMeshOverlayRenderer {
    private static final float EPSILON = 1E-3f;

    public TextureOverlayRenderer(Identifier texture) {
        this(texture, true);
    }

    public TextureOverlayRenderer(Identifier texture, boolean doIrisFlickerFix) {
        super(RenderPipelines.POSITION_TEX_COLOR_FOG_TRIANGLES, texture, doIrisFlickerFix);
    }

    @Override
    protected void addVertices(float x, float y, float z, OverlayRendererBlockData data) {
        OverlayVertexHelper.texturedSquare(
                buffer,
                OverlayVertexHelper.FixedAxis.Y, y + 1f + EPSILON,
                x, z,
                data.color(),
                data.textureSection(),
                UVRotation.NONE
        );
    }
}
