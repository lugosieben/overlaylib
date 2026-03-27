package net.lugo.overlaylib;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.lugo.overlaylib.test.OverlayTesting;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public class Overlay {
    private static final Minecraft MC = Minecraft.getInstance();

    private final OverlayRenderer renderer;
    private final OverlayManager overlayManager;
    private int chunkScanRadius;
    private boolean active = true;
    private boolean isRegistered = false;

    private int frameCounter;

    public Overlay(OverlayRenderer renderer, int initialChunkScanRadius, OverlayManager manager) {
        this.renderer = renderer;
        this.chunkScanRadius = initialChunkScanRadius;
        this.overlayManager = manager;
        overlayManager.setChunkScanRadius(chunkScanRadius);
        OverlayTesting.report("overlay", () -> "created radius=" + initialChunkScanRadius);
    }

    public OverlayManager getOverlayManager() {
        return overlayManager;
    }

    public void setActive(boolean isActive) {
        this.active = isActive;
        overlayManager.setActive(isActive);
        if (isActive) overlayManager.setChunkScanRadius(chunkScanRadius);
        OverlayTesting.report("overlay", () -> "setActive=" + isActive + ", radius=" + chunkScanRadius);
    }

    public void setChunkScanRadius(int radius) {
        this.chunkScanRadius = radius;
        overlayManager.setChunkScanRadius(radius);
        OverlayTesting.report("overlay", () -> "chunkScanRadius=" + radius);
    }

    public int getChunkScanRadius() {
        return this.chunkScanRadius;
    }

    @SuppressWarnings("DataFlowIssue")
    private void renderAllBlocks() {
        boolean reportThisFrame = OverlayTesting.shouldReport() && (++frameCounter % 120 == 0);
        int renderedBlocks = 0;
        int missingSections = 0;
        int consideredSections = 0;

        int playerChunkX = SectionPos.blockToSectionCoord(MC.player.getBlockX());
        int playerChunkZ = SectionPos.blockToSectionCoord(MC.player.getBlockZ());
        int effectiveChunkRadius = Math.min(chunkScanRadius, MC.options.getEffectiveRenderDistance() + 1);
        int radiusSquared = effectiveChunkRadius * effectiveChunkRadius;
        var visibleSections = MC.levelRenderer.getVisibleSections();

        for (var renderSection : visibleSections) {
            BlockPos renderOrigin = renderSection.getRenderOrigin();
            int sectionX = SectionPos.blockToSectionCoord(renderOrigin.getX());
            int sectionY = SectionPos.blockToSectionCoord(renderOrigin.getY());
            int sectionZ = SectionPos.blockToSectionCoord(renderOrigin.getZ());

            int dx = sectionX - playerChunkX;
            int dz = sectionZ - playerChunkZ;
            if (dx * dx + dz * dz > radiusSquared) {
                continue;
            }

            consideredSections++;
            OverlayRendererBlockData[] blocks = overlayManager.getSectionBlocks(sectionX, sectionY, sectionZ);
            if (blocks == null) {
                if (reportThisFrame) {
                    missingSections++;
                }
                continue;
            }
            if (reportThisFrame) {
                renderedBlocks += blocks.length;
            }
            for (OverlayRendererBlockData blockData : blocks) {
                renderer.addBlock(blockData);
            }
        }

        if (reportThisFrame) {
            int finalRenderedBlocks = renderedBlocks;
            int finalMissingSections = missingSections;
            int finalConsideredSections = consideredSections;
            OverlayTesting.report("overlay", () -> "frameSummary visibleSections=" + visibleSections.size()
                    + ", consideredSections=" + finalConsideredSections
                    + ", renderedBlocks=" + finalRenderedBlocks + ", pendingSections=" + finalMissingSections);
        }
    }

    public void register() {
        if (isRegistered) return;
        isRegistered = true;
        OverlayTesting.report("overlay", "registered render hook");
        LevelRenderEvents.END_MAIN.register((context -> {
            if (MC.player == null || MC.level == null || !active) return;
            ProfilerFiller profiler = Profiler.get();
            profiler.push("overlaylib");
            profiler.push("render");
            profiler.push("startBatch");
            renderer.startBatch(context);
            profiler.popPush("render");
            renderAllBlocks();
            profiler.popPush("endBatch");
            renderer.endBatch();
            profiler.pop();
            renderer.uploadThenDraw();
            profiler.pop();
            profiler.pop();
        }));
    }
}