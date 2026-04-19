package net.lugo.overlaylib;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.lugo.overlaylib.test.OverlayTesting;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;

public class Overlay {
    private static final Minecraft MC = Minecraft.getInstance();

    private final OverlayRenderer renderer;
    private final OverlayManager overlayManager;
    private int chunkScanRadius;
    private boolean active = true;
    private boolean isRegistered = false;

    private int frameCounter;
    private int lastPreparedPlayerChunkX = Integer.MIN_VALUE;
    private int lastPreparedPlayerChunkZ = Integer.MIN_VALUE;
    private int lastPreparedEffectiveRadius = Integer.MIN_VALUE;
    private int lastPreparedMinSectionY = Integer.MIN_VALUE;
    private int lastPreparedMaxSectionY = Integer.MIN_VALUE;

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
        if (!isActive) {
            lastPreparedPlayerChunkX = Integer.MIN_VALUE;
            lastPreparedPlayerChunkZ = Integer.MIN_VALUE;
            lastPreparedEffectiveRadius = Integer.MIN_VALUE;
            lastPreparedMinSectionY = Integer.MIN_VALUE;
            lastPreparedMaxSectionY = Integer.MIN_VALUE;
        }
        if (isActive) prepareSectionsIfNeeded();
        OverlayTesting.report("overlay", () -> "setActive=" + isActive + ", radius=" + chunkScanRadius);
    }

    public void setChunkScanRadius(int radius) {
        this.chunkScanRadius = radius;
        overlayManager.setChunkScanRadius(radius);
        if (active) prepareSectionsIfNeeded();
        OverlayTesting.report("overlay", () -> "chunkScanRadius=" + radius);
    }

    public int getChunkScanRadius() {
        return this.chunkScanRadius;
    }

    @SuppressWarnings("DataFlowIssue")
    private void renderAllBlocks() {
        prepareSectionsIfNeeded();

        boolean reportThisFrame = OverlayTesting.shouldReport() && (++frameCounter % 120 == 0);
        int renderedBlocks = 0;
        int missingSections = 0;
        int consideredSections = 0;

        int playerChunkX = SectionPos.blockToSectionCoord(MC.player.getBlockX());
        int playerChunkZ = SectionPos.blockToSectionCoord(MC.player.getBlockZ());
        int effectiveChunkRadius = Math.min(chunkScanRadius, MC.options.getEffectiveRenderDistance() + 1);
        int minSectionY = MC.level.getMinSectionY();
        int maxSectionY = MC.level.getMaxSectionY();


        List<SectionPos> sectionsInRadius = MC.levelRenderer.getVisibleSections().stream().map((s) -> SectionPos.of(s.getSectionNode())).toList();
        if (sectionsInRadius.isEmpty()) {
            // Sodium replaces the LevelRenderer so we have to get the sections another way
            sectionsInRadius = getSectionsInRadius(playerChunkX, playerChunkZ, effectiveChunkRadius, minSectionY, maxSectionY);
        }

        for (SectionPos sectionPos : sectionsInRadius) {
            consideredSections++;
            OverlayRendererBlockData[] blocks = overlayManager.getSectionBlocks(sectionPos);
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
            OverlayTesting.report("overlay", () -> "frameSummary consideredSections=" + finalConsideredSections
                    + ", renderedBlocks=" + finalRenderedBlocks + ", pendingSections=" + finalMissingSections);
        }
    }

    private List<SectionPos> getSectionsInRadius(int centerChunkX, int centerChunkZ, int radius, int minSectionY, int maxSectionY) {
        List<SectionPos> sections = new ArrayList<>();
        int radiusSquared = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSquared) {
                    continue;
                }

                int sectionX = centerChunkX + dx;
                int sectionZ = centerChunkZ + dz;
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    sections.add(SectionPos.of(sectionX, sectionY, sectionZ));
                }
            }
        }
        return sections;
    }

    private void prepareSectionsIfNeeded() {
        if (!active || MC.player == null || MC.level == null) {
            return;
        }

        int playerChunkX = SectionPos.blockToSectionCoord(MC.player.getBlockX());
        int playerChunkZ = SectionPos.blockToSectionCoord(MC.player.getBlockZ());
        int effectiveChunkRadius = Math.min(chunkScanRadius, MC.options.getEffectiveRenderDistance() + 1);
        int minSectionY = MC.level.getMinSectionY();
        int maxSectionY = MC.level.getMaxSectionY();

        if (playerChunkX == lastPreparedPlayerChunkX
                && playerChunkZ == lastPreparedPlayerChunkZ
                && effectiveChunkRadius == lastPreparedEffectiveRadius
                && minSectionY == lastPreparedMinSectionY
                && maxSectionY == lastPreparedMaxSectionY) {
            return;
        }

        lastPreparedPlayerChunkX = playerChunkX;
        lastPreparedPlayerChunkZ = playerChunkZ;
        lastPreparedEffectiveRadius = effectiveChunkRadius;
        lastPreparedMinSectionY = minSectionY;
        lastPreparedMaxSectionY = maxSectionY;

        for (SectionPos sectionPos : getSectionsInRadius(playerChunkX, playerChunkZ, effectiveChunkRadius, minSectionY, maxSectionY)) {
            overlayManager.prepareSection(sectionPos);
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