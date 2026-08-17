package net.lugo.overlaylib.managers;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.lugo.overlaylib.Overlay;
import net.lugo.overlaylib.OverlayLib;
import net.lugo.overlaylib.OverlayManager;
import net.lugo.overlaylib.test.OverlayTesting;
import net.lugo.overlaylib.util.DistanceUtil;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.lugo.overlaylib.util.ThreadUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class CachedOverlayManager implements OverlayManager {
    private static final Minecraft MC = Minecraft.getInstance();

    private static final Set<CachedOverlayManager> ACTIVE_CACHES = ConcurrentHashMap.newKeySet();
    private static boolean tickRegistered = false;

    private final Map<SectionPos, CacheSectionPosEntry> cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SectionPos, CacheSectionPosEntry> eldest) {
            return size() > maxCacheSize;
        }
    });
    private final Queue<SectionPos> computeQueue = new ConcurrentLinkedQueue<>();
    private final Set<SectionPos> queuedSections = ConcurrentHashMap.newKeySet();
    private final Set<SectionPos> importantSections = ConcurrentHashMap.newKeySet();
    private final Map<SectionPos, Long> sectionVersions = new ConcurrentHashMap<>();

    private final Function<BlockPos, OverlayRendererBlockData> computeFunction;

    private boolean closed;

    private int maxCacheSize;
    private int maxComputationsPerTick;
    private int chunkScanRadius;
    private int chunkScanRadiusVertical = Overlay.CHUNK_SCAN_RADIUS_VERTICAL_MAX;
    private boolean active;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private long lastReprioritizeNanos;
    private int processSummaryCounter;
    private int computeSummaryCounter;

    private static final long REPRIORITIZE_INTERVAL_NANOS = 200_000_000L;

    public CachedOverlayManager(Function<BlockPos, OverlayRendererBlockData> computeFunction) {
        this(computeFunction, 1024, 32);
    }

    public CachedOverlayManager(Function<BlockPos, OverlayRendererBlockData> computeFunction, int maxCacheSize, int maxComputationsPerTick) {
        this.maxCacheSize = maxCacheSize;
        this.maxComputationsPerTick = Math.max(1, maxComputationsPerTick);
        this.computeFunction = computeFunction;

        ACTIVE_CACHES.add(this);
        ensureTickRegistered();

        OverlayTesting.report("cache", () -> "created maxCacheSize=" + this.maxCacheSize
                + ", maxComputationsPerTick=" + this.maxComputationsPerTick);
    }

    public record CacheSectionPosEntry(SectionPos pos, OverlayRendererBlockData[] blocks, long lastAccessTime) { }

    public void prepareSection(SectionPos sectionPos) {
        if (queuedSections.add(sectionPos)) {
            computeQueue.offer(sectionPos);
        }
    }

    @Override
    public OverlayRendererBlockData[] getSectionBlocks(SectionPos sectionPos) {
        return getSectionBlocks(sectionPos.x(), sectionPos.y(), sectionPos.z());
    }

    @Override
    public OverlayRendererBlockData[] getSectionBlocks(int sectionX, int sectionY, int sectionZ) {
        SectionPos sectionPos = SectionPos.of(sectionX, sectionY, sectionZ);
        CacheSectionPosEntry entry = cache.get(sectionPos);
        if (entry != null) {
            return entry.blocks();
        }

        if (importantSections.remove(sectionPos) && compute(sectionPos)) {
            CacheSectionPosEntry recomputedEntry = cache.get(sectionPos);
            if (recomputedEntry != null) {
                return recomputedEntry.blocks();
            }
        }

        prepareSection(sectionPos);
        return null;
    }

    @Override
    public long getSectionVersion(SectionPos sectionPos) {
        return sectionVersions.getOrDefault(sectionPos, 0L);
    }


    public void processQueue() {
        if (isProcessing.compareAndSet(false, true)) {
            ThreadUtil.submit(this::processQueueInternal);
        }
    }

    private void processQueueInternal() {
        try {
            if (MC.player == null || MC.level == null || !active) return;

            removeOldEntries();

            computePlayerArea();

            if (computeQueue.size() > maxComputationsPerTick * 4 && shouldReprioritize()) {
                reprioritizeQueue();
            }

            int processed = 0;
            while (processed < maxComputationsPerTick && !computeQueue.isEmpty()) {
                SectionPos sectionPos = computeQueue.poll();
                if (sectionPos == null) break;
                queuedSections.remove(sectionPos);
                if (!cache.containsKey(sectionPos)) {
                    compute(sectionPos);
                }
                processed++;
            }

            if (OverlayTesting.shouldReport() && (++processSummaryCounter % 100 == 0)) {
                int processedCount = processed;
                int cacheSize = cache.size();
                int queuedSize = queuedSections.size();
                int queuedCompute = computeQueue.size();
                int importantCount = importantSections.size();
                OverlayTesting.report("cache", () -> "processSummary processed=" + processedCount
                        + ", cacheSize=" + cacheSize
                        + ", queuedSections=" + queuedSize
                        + ", computeQueue=" + queuedCompute
                        + ", importantSections=" + importantCount);
            }
        } catch (Exception e) {
            OverlayLib.LOGGER.error("Error in OverlayLib worker", e);
            OverlayTesting.report("cache", () -> "worker exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            isProcessing.set(false);
        }
    }

    private boolean shouldReprioritize() {
        long now = System.nanoTime();
        if (now - lastReprioritizeNanos < REPRIORITIZE_INTERVAL_NANOS) {
            return false;
        }
        lastReprioritizeNanos = now;
        return true;
    }

    private void computePlayerArea() {
        @SuppressWarnings("DataFlowIssue")
        SectionPos center = SectionPos.of(MC.player.blockPosition());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                SectionPos sectionPos = SectionPos.of(center.x() + dx, center.y(), center.z() + dz);
                if (!cache.containsKey(sectionPos) && !importantSections.contains(sectionPos)) {
                    compute(sectionPos);
                }
            }
        }
    }

    private void reprioritizeQueue() {
        if (MC.player == null) return;
        int playerBlockX = MC.player.getBlockX();
        int playerBlockY = MC.player.getBlockY();
        int playerBlockZ = MC.player.getBlockZ();

        int playerSectionX = SectionPos.blockToSectionCoord(playerBlockX);
        int playerSectionZ = SectionPos.blockToSectionCoord(playerBlockZ);
        int maxHorizontalDistanceSquared = chunkScanRadius * chunkScanRadius;

        List<SectionPos> sections = new ArrayList<>();
        SectionPos pos;
        while ((pos = computeQueue.poll()) != null) {
            int dx = pos.x() - playerSectionX;
            int dz = pos.z() - playerSectionZ;
            if (dx * dx + dz * dz <= maxHorizontalDistanceSquared) {
                sections.add(pos);
            } else {
                queuedSections.remove(pos);
            }
        }

        sections.sort(Comparator.comparingDouble(a -> DistanceUtil.getDistanceSquared(a, playerBlockX, playerBlockY, playerBlockZ)));
        computeQueue.addAll(sections);

        OverlayTesting.report("cache", () -> "reprioritized queue size=" + sections.size());
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
        clearAll();
        if (active) {
            updateCacheSizing();
        }
        OverlayTesting.report("cache", () -> "setActive=" + active);
    }

    @Override
    public void setChunkScanRadius(int radius) {
        this.chunkScanRadius = radius;
        updateCacheSizing();
    }

    @Override
    public void setChunkScanRadiusVertical(int radius) {
        this.chunkScanRadiusVertical = radius;
        updateCacheSizing();
    }

    private void updateCacheSizing() {
        if (MC.level == null) return;

        if (!active) return;

        int requiredSections = getRequiredSections(chunkScanRadius, chunkScanRadiusVertical);

        if (requiredSections > maxCacheSize) {
            int oldMaxCacheSize = maxCacheSize;
            updateMaxCacheSize(requiredSections);
            OverlayLib.LOGGER.info("Resizing maxCacheSize, total sections * 2 ({}) is higher than current limit {}. New size is {}", requiredSections, oldMaxCacheSize, requiredSections);
            OverlayTesting.report("cache", () -> "resized max cache to " + requiredSections + " for radius=" + chunkScanRadius + ", verticalRadius=" + chunkScanRadiusVertical);
        }
    }

    public void setMaxComputationsPerTick(int maxComputationsPerTick) {
        this.maxComputationsPerTick = Math.max(1, maxComputationsPerTick);
    }

    @SuppressWarnings("DataFlowIssue")
    private static int getRequiredSections(int chunkScanRadius, int chunkScanRadiusVertical) {
        int horizontalChunks = 0;
        for (int dx = -chunkScanRadius; dx <= chunkScanRadius; dx++) {
            for (int dz = -chunkScanRadius; dz <= chunkScanRadius; dz++) {
                if (dx * dx + dz * dz <= chunkScanRadius * chunkScanRadius) {
                    horizontalChunks++;
                }
            }
        }
        int worldVerticalSections = MC.level.getMaxSectionY() - MC.level.getMinSectionY() + 1;
        int verticalSections = chunkScanRadiusVertical == Overlay.CHUNK_SCAN_RADIUS_VERTICAL_MAX
                ? worldVerticalSections
                : Math.min(worldVerticalSections, chunkScanRadiusVertical * 2 + 1);
        int totalSections = horizontalChunks * verticalSections;
        return totalSections * 2;
    }

    public void updateMaxCacheSize(int newMaxCacheSize) {
        if (newMaxCacheSize < 1) return;
        this.maxCacheSize = newMaxCacheSize;
        removeOldEntries();
        OverlayTesting.report("cache", () -> "updateMaxCacheSize=" + newMaxCacheSize);
    }

    public void removeOldEntries() {
        removeOldEntries(maxCacheSize);
    }

    public void removeOldEntries(int targetSize) {
        synchronized (cache) {
            if (cache.size() > targetSize) {
                 Iterator<SectionPos> it = cache.keySet().iterator();
                 while (it.hasNext() && cache.size() > targetSize) {
                     it.next();
                     it.remove();
                 }
            }
        }
    }

    private boolean compute(SectionPos sectionPos) {
        if (MC.level == null || MC.player == null) return false;
        if (!MC.level.hasChunk(sectionPos.x(), sectionPos.z())) return false;

        List<OverlayRendererBlockData> renderableBlocks = new ArrayList<>(256);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        int minX = SectionPos.sectionToBlockCoord(sectionPos.getX());
        int minY = SectionPos.sectionToBlockCoord(sectionPos.getY());
        int minZ = SectionPos.sectionToBlockCoord(sectionPos.getZ());

        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    mutableBlockPos.set(minX + x, minY + y, minZ + z);
                    OverlayRendererBlockData data;
                    try {
                        data = computeFunction.apply(mutableBlockPos);
                    } catch (Exception e) {
                        OverlayLib.LOGGER.error("Error computing block data at {}: {}. This is most likely an issue with a mod using OverlayLib, not OverlayLib itself.", mutableBlockPos, e.getMessage(), e);
                        continue;
                    }
                    if (data.shouldRender()) {
                        if (data.pos() == mutableBlockPos) {
                            data = new OverlayRendererBlockData(mutableBlockPos.immutable(), data.color(), data.yOffset(), data.textureSection());
                        }
                        renderableBlocks.add(data);
                    }
                }
            }
        }

        OverlayRendererBlockData[] blocksArray = renderableBlocks.toArray(OverlayRendererBlockData[]::new);
        cache.put(sectionPos, new CacheSectionPosEntry(sectionPos, blocksArray, System.currentTimeMillis()));
        bumpVersion(sectionPos);

        if (OverlayTesting.shouldReport() && (++computeSummaryCounter % 200 == 0)) {
            int blockCount = blocksArray.length;
            OverlayTesting.report("cache", () -> "computeSummary section=" + sectionPos.x() + "," + sectionPos.y() + "," + sectionPos.z()
                    + ", renderableBlocks=" + blockCount);
        }

        return true;
    }

    public boolean clear(SectionPos sectionPos) {
        boolean removedCached = cache.remove(sectionPos) != null;
        boolean removedQueued = queuedSections.remove(sectionPos) | computeQueue.remove(sectionPos);
        if (removedCached || removedQueued) {
            bumpVersion(sectionPos);
            importantSections.add(sectionPos);
            OverlayTesting.report("cache", () -> "cleared section " + sectionPos.x() + "," + sectionPos.y() + "," + sectionPos.z());
            return true;
        }
        return false;
    }

    public void refresh(SectionPos sectionPos) {
        clear(sectionPos);
    }

    public boolean clear(BlockPos blockPos) {
        SectionPos sectionPos = SectionPos.of(blockPos);
        return clear(sectionPos);
    }

    public void refresh(BlockPos blockPos) {
        SectionPos sectionPos = SectionPos.of(blockPos);
        refresh(sectionPos);
    }

    public void clearAll() {
        cache.clear();
        computeQueue.clear();
        queuedSections.clear();
        importantSections.clear();
        sectionVersions.clear();
        OverlayTesting.report("cache", "cleared all cache state");
    }

    @Override
    public void clearCache() {
        clearAll();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        ACTIVE_CACHES.remove(this);
        active = false;
        clearAll();
        OverlayTesting.report("cache", "closed");
    }

    private void bumpVersion(SectionPos sectionPos) {
        sectionVersions.merge(sectionPos, 1L, Long::sum);
    }

    private static void ensureTickRegistered() {
        if (tickRegistered) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> ACTIVE_CACHES.forEach(CachedOverlayManager::processQueue));
        tickRegistered = true;
        OverlayTesting.report("cache", "registered cache tick processing");
    }
}

