package net.lugo.overlaylib;

import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.minecraft.core.SectionPos;

public interface OverlayManager {
    OverlayRendererBlockData[] getSectionBlocks(SectionPos sectionPos);

    default OverlayRendererBlockData[] getSectionBlocks(int sectionX, int sectionY, int sectionZ) {
        return getSectionBlocks(SectionPos.of(sectionX, sectionY, sectionZ));
    }

    default long getSectionVersion(SectionPos sectionPos) {
        return 0L;
    }

    default void prepareSection(SectionPos sectionPos) {}

    default void setActive(boolean active) {}

    default void setChunkScanRadius(int radius) {}

    default void setChunkScanRadiusVertical(int radius) {}

    default void clearCache() {}

    default void close() {}
}
