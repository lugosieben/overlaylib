package net.lugo.overlaylib;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.lugo.overlaylib.util.DistanceUtil;
import net.lugo.overlaylib.util.OverlayManagerUpdateData;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Overlay {
    private static final Minecraft MC = Minecraft.getInstance();

    private final OverlayRenderer renderer;
    private final OverlayManager overlayManager;
    private int chunkScanRadius;

    public Overlay(OverlayRenderer renderer, int initialChunkScanRadius, OverlayManager manager) {
        this.renderer = renderer;
        this.chunkScanRadius = initialChunkScanRadius;
        this.overlayManager = manager;
    }

    public Optional<OverlayManager> getOverlayManager() {
        return Optional.ofNullable(overlayManager);
    }

    public void setChunkScanRadius(int radius) {
        this.chunkScanRadius = radius;
    }

    public int getChunkScanRadius() {
        return this.chunkScanRadius;
    }

    @SuppressWarnings("DataFlowIssue")
    private void renderAllBlocks() {
        int playerChunkX = (int) Math.floor(MC.player.getX() / 16.0);
        int playerChunkZ = (int) Math.floor(MC.player.getZ() / 16.0);
        List<SectionPos> sectionsToRender = new ArrayList<>();
        BlockPos playerPos = MC.player.blockPosition();
        int effectiveChunkRadius = Math.min(chunkScanRadius, MC.options.getEffectiveRenderDistance() + 1);

        overlayManager.update(new OverlayManagerUpdateData(chunkScanRadius));

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
            OverlayRendererBlockData[] blocks = overlayManager.getSectionBlocks(sectionPos);
            if (blocks == null) continue;
            for (OverlayRendererBlockData blockData : blocks) {
                renderer.addBlock(blockData);
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