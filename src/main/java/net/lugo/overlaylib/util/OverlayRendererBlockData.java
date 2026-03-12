package net.lugo.overlaylib.util;

import net.minecraft.core.BlockPos;

import java.util.Optional;

public record OverlayRendererBlockData(BlockPos pos, float r, float g, float b, float yOffset, Optional<TextureSection> textureSection) {
    public boolean shouldRender() {
        return pos != null;
    }
    public final static OverlayRendererBlockData NO_RENDER = new OverlayRendererBlockData(null, 0f, 0f, 0f, 0f, Optional.empty());
}
