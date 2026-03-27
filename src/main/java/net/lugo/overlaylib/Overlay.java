package net.lugo.overlaylib;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.lugo.overlaylib.test.OverlayTesting;
import net.lugo.overlaylib.util.DistanceUtil;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.*;

public class Overlay {
    private static final Minecraft MC = Minecraft.getInstance();

    private final OverlayRenderer renderer;
    private final OverlayManager overlayManager;
    private int chunkScanRadius;
    private boolean active = true;
    private boolean isRegistered = false;

    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;
    private int lastEffectiveChunkRadius = -1;
    private final List<SectionPos> cachedSections = new ArrayList<>();
    private int frameCounter;

    private record SectionDistance(SectionPos sectionPos, double distanceSquared) {}

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
        this.lastEffectiveChunkRadius = -1;
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

        int playerChunkX = (int) Math.floor(MC.player.getX() / 16.0);
        int playerChunkZ = (int) Math.floor(MC.player.getZ() / 16.0);
        int playerBlockX = MC.player.getBlockX();
        int playerBlockY = MC.player.getBlockY();
        int playerBlockZ = MC.player.getBlockZ();
        int effectiveChunkRadius = Math.min(chunkScanRadius, MC.options.getEffectiveRenderDistance() + 1);

        if (playerChunkX != lastPlayerChunkX || playerChunkZ != lastPlayerChunkZ || effectiveChunkRadius != lastEffectiveChunkRadius) {
            cachedSections.clear();
            List<SectionDistance> sectionDistances = new ArrayList<>();
            for (int dx = -effectiveChunkRadius; dx <= effectiveChunkRadius; dx++) {
                for (int dz = -effectiveChunkRadius; dz <= effectiveChunkRadius; dz++) {
                    if (dx * dx + dz * dz > effectiveChunkRadius * effectiveChunkRadius) continue;
                    int chunkX = playerChunkX + dx;
                    int chunkZ = playerChunkZ + dz;
                    for (int sectionY = MC.level.getMinSectionY(); sectionY <= MC.level.getMaxSectionY(); sectionY++) {
                        SectionPos sectionPos = SectionPos.of(chunkX, sectionY, chunkZ);
                        double distanceSquared = DistanceUtil.getDistanceSquared(sectionPos, playerBlockX, playerBlockY, playerBlockZ);
                        sectionDistances.add(new SectionDistance(sectionPos, distanceSquared));
                    }
                }
            }

            sectionDistances.sort(Comparator.comparingDouble(SectionDistance::distanceSquared));
            for (SectionDistance sectionDistance : sectionDistances) {
                cachedSections.add(sectionDistance.sectionPos());
            }

            lastPlayerChunkX = playerChunkX;
            lastPlayerChunkZ = playerChunkZ;
            lastEffectiveChunkRadius = effectiveChunkRadius;

            OverlayTesting.report("overlay", () -> "rebuilt section order: sections=" + cachedSections.size()
                    + ", playerChunk=" + playerChunkX + "," + playerChunkZ + ", effectiveRadius=" + effectiveChunkRadius);
        }

        for (SectionPos sectionPos : cachedSections) {
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
            OverlayTesting.report("overlay", () -> "frameSummary sections=" + cachedSections.size()
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