package net.lugo.overlaylib.util;

import net.minecraft.core.BlockPos;

public record OverlayRendererBlockData(BlockPos pos, float r, float g, float b, float yOffset, TextureSection textureSection) {
    public OverlayRendererBlockData(BlockPos pos, float r, float g, float b, float yOffset) {
        this(pos, r, g, b, yOffset, TextureSection.SINGULAR);
    }
    public boolean shouldRender() {
        return pos != null;
    }
    public final static OverlayRendererBlockData NO_RENDER = new OverlayRendererBlockData(null, 0f, 0f, 0f, 0f);
}
