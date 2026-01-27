package net.lugo.overlaylib;

import net.lugo.overlaylib.util.OverlayManagerUpdateData;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.minecraft.core.SectionPos;

public interface OverlayManager {
    OverlayRendererBlockData[] getSectionBlocks(SectionPos sectionPos);

    default void update(OverlayManagerUpdateData data) {}
}
