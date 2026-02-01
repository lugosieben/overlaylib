package net.lugo.overlaylib.managers;

import net.lugo.overlaylib.OverlayManager;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class SimpleOverlayManager implements OverlayManager {
    private final Function<BlockPos, OverlayRendererBlockData> computeFunction;

    public SimpleOverlayManager(Function<BlockPos, OverlayRendererBlockData> computeFunction) {
        this.computeFunction = computeFunction;
    }

    @Override
    public OverlayRendererBlockData[] getSectionBlocks(SectionPos sectionPos) {
        if (computeFunction == null) return null;
        List<OverlayRendererBlockData> renderableBlocks = new ArrayList<>();
        int minX = SectionPos.sectionToBlockCoord(sectionPos.getX());
        int minY = SectionPos.sectionToBlockCoord(sectionPos.getY());
        int minZ = SectionPos.sectionToBlockCoord(sectionPos.getZ());
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    BlockPos blockPos = new BlockPos(minX + x, minY + y, minZ + z);
                    OverlayRendererBlockData data = computeFunction.apply(blockPos);
                    if (data.shouldRender()) {
                        renderableBlocks.add(data);
                    }
                }
            }
        }
        return renderableBlocks.toArray(OverlayRendererBlockData[]::new);
    }
}
