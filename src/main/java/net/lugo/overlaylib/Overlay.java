package net.lugo.overlaylib;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.lugo.overlaylib.util.DistanceUtil;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public record Overlay(OverlayRenderer renderer, Function<BlockPos, OverlayRendererBlockData> blockDataFunction) {
    private static final Minecraft MC = Minecraft.getInstance();

    @SuppressWarnings("DataFlowIssue")
    private void renderAllBlocks() {
        int playerChunkX = (int) Math.floor(MC.player.getX() / 16.0);
        int playerChunkZ = (int) Math.floor(MC.player.getZ() / 16.0);
        List<SectionPos> sectionsToRender = new ArrayList<>();
        BlockPos playerPos = MC.player.blockPosition();
        int effectiveChunkRadius = 1;

        for (int dx = -effectiveChunkRadius; dx <= effectiveChunkRadius; dx++) {
            for (int dz = -effectiveChunkRadius; dz <= effectiveChunkRadius; dz++) {
                if (dx * dx + dz * dz > effectiveChunkRadius * effectiveChunkRadius) continue;
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                for (int sectionY = MC.level.getMinSectionY(); sectionY <= MC.level.getMaxSectionY(); sectionY++) {
                    sectionsToRender.add(SectionPos.of(chunkX, sectionY, chunkZ));
                }
            }
        }

        sectionsToRender.sort((a, b) -> {
            double distA = DistanceUtil.getDistanceSquared(a, playerPos);
            double distB = DistanceUtil.getDistanceSquared(b, playerPos);
            return Double.compare(distA, distB);
        });

        for (SectionPos sectionPos : sectionsToRender) {
            int minX = SectionPos.sectionToBlockCoord(sectionPos.getX());
            int minY = SectionPos.sectionToBlockCoord(sectionPos.getY());
            int minZ = SectionPos.sectionToBlockCoord(sectionPos.getZ());

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockPos blockPos = new BlockPos(minX + x, minY + y, minZ + z);
                        OverlayRendererBlockData blockData = blockDataFunction.apply(blockPos);
                        if (blockData.shouldRender()) {
                            renderer.addBlock(blockData);
                        }
                    }
                }
            }
        }
    }

    public void register() {
        WorldRenderEvents.END_MAIN.register((context -> {
            if (MC.player == null || MC.level == null) return;
            renderer.startBatch(context);
            renderAllBlocks();
            renderer.endBatch();
            renderer.uploadThenDraw();
        }));
    }
}
