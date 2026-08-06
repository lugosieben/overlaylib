package net.lugo.overlaylib;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.lugo.overlaylib.test.OverlayTesting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public class Overlay {
    private static final Minecraft MC = Minecraft.getInstance();

    public static final int CHUNK_SCAN_RADIUS_VERTICAL_MAX = -1;

    private final OverlayRenderer renderer;
    private final OverlayManager overlayManager;
    private int chunkScanRadius;
    private int chunkScanRadiusVertical;
    private boolean active = true;
    private boolean isRegistered = false;
    private boolean isClosed = false;
    private BooleanSupplier renderFilter = () -> true;

    private int frameCounter;
    private int lastPreparedPlayerChunkX = Integer.MIN_VALUE;
    private int lastPreparedPlayerChunkZ = Integer.MIN_VALUE;
    private int lastPreparedPlayerChunkY = Integer.MIN_VALUE;
    private int lastPreparedEffectiveRadius = Integer.MIN_VALUE;
    private int lastPreparedVerticalRadius = Integer.MIN_VALUE;
    private int lastPreparedMinSectionY = Integer.MIN_VALUE;
    private int lastPreparedMaxSectionY = Integer.MIN_VALUE;

    public Overlay(OverlayRenderer renderer, int initialChunkScanRadius, int initialChunkScanRadiusVertical, OverlayManager manager) {
        this.renderer = renderer;
        this.chunkScanRadius = initialChunkScanRadius;
        this.chunkScanRadiusVertical = initialChunkScanRadiusVertical;
        this.overlayManager = manager;
        overlayManager.setChunkScanRadius(chunkScanRadius);
        overlayManager.setChunkScanRadiusVertical(chunkScanRadiusVertical);
        OverlayTesting.report("overlay", () -> "created radius=" + initialChunkScanRadius + "/" + initialChunkScanRadiusVertical);
    }

    public OverlayManager getOverlayManager() {
        return overlayManager;
    }

    public void setRenderFilter(BooleanSupplier renderFilter) {
        this.renderFilter = Objects.requireNonNull(renderFilter, "renderFilter");
        OverlayTesting.report("overlay", () -> "renderFilter=" + renderFilter);
    }

    public BooleanSupplier getRenderFilter() {
        return renderFilter;
    }

    public void setActive(boolean isActive) {
        if (isClosed) return;
        this.active = isActive;
        overlayManager.setActive(isActive);
        renderer.clearCache();
        if (isActive) {
            overlayManager.setChunkScanRadius(chunkScanRadius);
            overlayManager.setChunkScanRadiusVertical(chunkScanRadiusVertical);
        }
        if (!isActive) {
            resetPreparedState();
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

    public void setChunkScanRadiusVertical(int radius) {
        this.chunkScanRadiusVertical = radius;
        overlayManager.setChunkScanRadiusVertical(this.chunkScanRadiusVertical);
        if (active) prepareSectionsIfNeeded();
        OverlayTesting.report("overlay", () -> "chunkScanRadiusVertical=" + this.chunkScanRadiusVertical);
    }

    public int getChunkScanRadiusVertical() {
        return this.chunkScanRadiusVertical;
    }

    @SuppressWarnings("DataFlowIssue")
    private void renderAllBlocks() {
        prepareSectionsIfNeeded();

        boolean reportThisFrame = OverlayTesting.shouldReport() && (++frameCounter % 120 == 0);

        int playerChunkX = SectionPos.blockToSectionCoord(MC.player.getBlockX());
        int playerChunkZ = SectionPos.blockToSectionCoord(MC.player.getBlockZ());
        int playerChunkY = SectionPos.blockToSectionCoord(MC.player.getBlockY());
        int effectiveChunkRadius = Math.min(chunkScanRadius, MC.options.getEffectiveRenderDistance() + 1);
        int effectiveVerticalRadius = chunkScanRadiusVertical;
        int minSectionY = MC.level.getMinSectionY();
        int maxSectionY = MC.level.getMaxSectionY();


        List<SectionPos> sectionsInRadius = MC.levelRenderer.visibleSections().stream()
                .map((s) -> SectionPos.of(s.getSectionNode()))
                .filter((s) -> (s.getX() - playerChunkX) * (s.getX() - playerChunkX) + (s.getZ() - playerChunkZ) * (s.getZ() - playerChunkZ) <= effectiveChunkRadius * effectiveChunkRadius)
                .filter((s) -> isWithinVerticalRange(s.getY(), playerChunkY, effectiveVerticalRadius))
                .toList();
        if (sectionsInRadius.isEmpty()) {
            // Sodium replaces the LevelRenderer so we have to get the sections another way
            sectionsInRadius = getSectionsInRadius(playerChunkX, playerChunkY, playerChunkZ, effectiveChunkRadius, effectiveVerticalRadius, minSectionY, maxSectionY);
        }

        OverlayRenderer.RenderResult result = renderer.renderSections(overlayManager, sectionsInRadius);

        if (reportThisFrame) {
            int finalDrawnSections = result.drawnSections();
            int finalBuiltSections = result.builtSections();
            int finalMissingSections = result.missingSections();
            int finalConsideredSections = result.consideredSections();
            OverlayTesting.report("overlay", () -> "frameSummary consideredSections=" + finalConsideredSections
                    + ", drawnSections=" + finalDrawnSections + ", builtSections=" + finalBuiltSections
                    + ", missingSections=" + finalMissingSections);
        }
    }

    private List<SectionPos> getSectionsInRadius(int centerChunkX, int centerChunkY, int centerChunkZ, int radius, int verticalRadius, int minSectionY, int maxSectionY) {
        List<SectionPos> sections = new ArrayList<>();
        int radiusSquared = radius * radius;
        int minY = getMinSectionY(centerChunkY, verticalRadius, minSectionY);
        int maxY = getMaxSectionY(centerChunkY, verticalRadius, maxSectionY);
        if (minY > maxY) {
            return sections;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSquared) {
                    continue;
                }

                int sectionX = centerChunkX + dx;
                int sectionZ = centerChunkZ + dz;
                for (int sectionY = minY; sectionY <= maxY; sectionY++) {
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
        int playerChunkY = SectionPos.blockToSectionCoord(MC.player.getBlockY());
        int effectiveChunkRadius = Math.min(chunkScanRadius, MC.options.getEffectiveRenderDistance() + 1);
        int effectiveVerticalRadius = chunkScanRadiusVertical;
        int minSectionY = MC.level.getMinSectionY();
        int maxSectionY = MC.level.getMaxSectionY();

        if (playerChunkX == lastPreparedPlayerChunkX
                && playerChunkZ == lastPreparedPlayerChunkZ
                && (effectiveVerticalRadius == CHUNK_SCAN_RADIUS_VERTICAL_MAX || playerChunkY == lastPreparedPlayerChunkY)
                && effectiveChunkRadius == lastPreparedEffectiveRadius
                && effectiveVerticalRadius == lastPreparedVerticalRadius
                && minSectionY == lastPreparedMinSectionY
                && maxSectionY == lastPreparedMaxSectionY) {
            return;
        }

        lastPreparedPlayerChunkX = playerChunkX;
        lastPreparedPlayerChunkZ = playerChunkZ;
        lastPreparedPlayerChunkY = playerChunkY;
        lastPreparedEffectiveRadius = effectiveChunkRadius;
        lastPreparedVerticalRadius = effectiveVerticalRadius;
        lastPreparedMinSectionY = minSectionY;
        lastPreparedMaxSectionY = maxSectionY;

        for (SectionPos sectionPos : getSectionsInRadius(playerChunkX, playerChunkY, playerChunkZ, effectiveChunkRadius, effectiveVerticalRadius, minSectionY, maxSectionY)) {
            overlayManager.prepareSection(sectionPos);
        }
    }

    private static boolean isWithinVerticalRange(int sectionY, int playerSectionY, int verticalRadius) {
        return verticalRadius == CHUNK_SCAN_RADIUS_VERTICAL_MAX || Math.abs(sectionY - playerSectionY) <= verticalRadius;
    }

    private void resetPreparedState() {
        lastPreparedPlayerChunkX = Integer.MIN_VALUE;
        lastPreparedPlayerChunkZ = Integer.MIN_VALUE;
        lastPreparedPlayerChunkY = Integer.MIN_VALUE;
        lastPreparedEffectiveRadius = Integer.MIN_VALUE;
        lastPreparedVerticalRadius = Integer.MIN_VALUE;
        lastPreparedMinSectionY = Integer.MIN_VALUE;
        lastPreparedMaxSectionY = Integer.MIN_VALUE;
    }

    private static int getMinSectionY(int playerSectionY, int verticalRadius, int minSectionY) {
        return verticalRadius == CHUNK_SCAN_RADIUS_VERTICAL_MAX ? minSectionY : Math.max(minSectionY, playerSectionY - verticalRadius);
    }

    private static int getMaxSectionY(int playerSectionY, int verticalRadius, int maxSectionY) {
        return verticalRadius == CHUNK_SCAN_RADIUS_VERTICAL_MAX ? maxSectionY : Math.min(maxSectionY, playerSectionY + verticalRadius);
    }

    public void register() {
        if (isRegistered || isClosed) return;
        isRegistered = true;
        OverlayTesting.report("overlay", "registered render hook");
        LevelRenderEvents.END_MAIN.register((context -> {
            if (isClosed || MC.player == null || MC.level == null || !active || !renderFilter.getAsBoolean()) return;
            ProfilerFiller profiler = Profiler.get();
            profiler.push("overlaylib");
            profiler.push("render");
            renderer.startBatch(context);
            profiler.popPush("draw");
            renderAllBlocks();
            profiler.popPush("endBatch");
            renderer.endBatch();
            profiler.pop();
            profiler.pop();
        }));
    }

    public void close() {
        if (isClosed) return;
        isClosed = true;
        active = false;
        renderFilter = () -> false;
        overlayManager.setActive(false);
        overlayManager.close();
        renderer.close();
        resetPreparedState();
        OverlayTesting.report("overlay", () -> "closed");
    }
}