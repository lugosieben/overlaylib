package net.lugo.overlaylib.util;

import net.minecraft.core.BlockPos;

import java.awt.Color;

public record OverlayRendererBlockData(BlockPos pos, Color color, float yOffset, TextureSection textureSection) {
    public OverlayRendererBlockData(BlockPos pos, Color color, float yOffset) {
        this(pos, color, yOffset, TextureSection.SINGULAR);
    }
    public boolean shouldRender() {
        return pos != null;
    }
    public final static OverlayRendererBlockData NO_RENDER = new OverlayRendererBlockData(null, Color.BLACK, 0f);
}
