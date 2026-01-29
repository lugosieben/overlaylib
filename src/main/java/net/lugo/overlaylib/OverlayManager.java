package net.lugo.overlaylib;

import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.minecraft.core.SectionPos;

public interface OverlayManager {
    OverlayRendererBlockData[] getSectionBlocks(SectionPos sectionPos);

    default void setActive(boolean active) {}

    default void setChunkScanRadius(int radius) {}
}
