package net.lugo.overlaylib.managers;

import net.lugo.overlaylib.OverlayManager;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class SimpleOverlayManager implements OverlayManager {
    private static final Minecraft MC = Minecraft.getInstance();

    private final Function<BlockPos, OverlayRendererBlockData> computeFunction;

    public SimpleOverlayManager(Function<BlockPos, OverlayRendererBlockData> computeFunction) {
        this.computeFunction = computeFunction;
    }

    @Override
    public long getSectionVersion(SectionPos sectionPos) {
        return MC.level == null ? 0L : MC.level.getGameTime();
    }

    @Override
    public OverlayRendererBlockData[] getSectionBlocks(SectionPos sectionPos) {
        return getSectionBlocks(sectionPos.x(), sectionPos.y(), sectionPos.z());
    }

    @Override
    public OverlayRendererBlockData[] getSectionBlocks(int sectionX, int sectionY, int sectionZ) {
        List<OverlayRendererBlockData> renderableBlocks = new ArrayList<>();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        int minX = SectionPos.sectionToBlockCoord(sectionX);
        int minY = SectionPos.sectionToBlockCoord(sectionY);
        int minZ = SectionPos.sectionToBlockCoord(sectionZ);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    mutableBlockPos.set(minX + x, minY + y, minZ + z);
                    OverlayRendererBlockData data = computeFunction.apply(mutableBlockPos);
                    if (data.shouldRender()) {
                        if (data.pos() == mutableBlockPos) {
                            data = new OverlayRendererBlockData(mutableBlockPos.immutable(), data.r(), data.g(), data.b(), data.yOffset(), data.textureSection());
                        }
                        renderableBlocks.add(data);
                    }
                }
            }
        }
        return renderableBlocks.toArray(OverlayRendererBlockData[]::new);
    }
}
