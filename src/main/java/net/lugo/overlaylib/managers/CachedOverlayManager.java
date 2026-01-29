package net.lugo.overlaylib.managers;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.lugo.overlaylib.OverlayLib;
import net.lugo.overlaylib.OverlayManager;
import net.lugo.overlaylib.util.DistanceUtil;
import net.lugo.overlaylib.util.OverlayManagerUpdateData;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

public class CachedOverlayManager implements OverlayManager {
    private static final Minecraft MC = Minecraft.getInstance();

    private static final Set<CachedOverlayManager> ACTIVE_CACHES = ConcurrentHashMap.newKeySet();
    private static boolean tickRegistered = false;

    private final Map<SectionPos, CacheSectionPosEntry> cache = new ConcurrentHashMap<>();
    private final Queue<SectionPos> computeQueue = new ConcurrentLinkedQueue<>();
    private final Set<SectionPos> queuedSections = ConcurrentHashMap.newKeySet();

    private final Function<BlockPos, OverlayRendererBlockData> computeFunction;

    private int maxCacheSize;
    private final int maxComputationsPerTick;
    private boolean active;

    public CachedOverlayManager(Function<BlockPos, OverlayRendererBlockData> computeFunction) {
        this(computeFunction, 1024, 32);
    }

    public CachedOverlayManager(Function<BlockPos, OverlayRendererBlockData> computeFunction, int maxCacheSize, int maxComputationsPerTick) {
        this.maxCacheSize = maxCacheSize;
        this.maxComputationsPerTick = Math.max(1, maxComputationsPerTick);
        this.computeFunction = computeFunction;

        ACTIVE_CACHES.add(this);
        ensureTickRegistered();
    }

    public record CacheSectionPosEntry(SectionPos pos, OverlayRendererBlockData[] blocks, long lastAccessTime) {
        CacheSectionPosEntry touch() {
            return new CacheSectionPosEntry(pos, blocks, System.currentTimeMillis());
        }
    }

    public void requestSection(SectionPos sectionPos) {
        if (cache.containsKey(sectionPos)) return;
        if (queuedSections.add(sectionPos)) {
            computeQueue.offer(sectionPos);
        }
    }

    @Override
    public OverlayRendererBlockData[] getSectionBlocks(SectionPos sectionPos) {
        requestSection(sectionPos);
        CacheSectionPosEntry entry = cache.get(sectionPos);
        if (entry == null) return null;
        CacheSectionPosEntry updated = entry.touch();
        cache.put(sectionPos, updated);
        return updated.blocks();
    }


    public void processQueue() {
        if (MC.player == null || MC.level == null || !active) return;

        if (computeQueue.size() > maxComputationsPerTick * 2) {
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
    }

    private void reprioritizeQueue() {
        BlockPos playerPos = MC.player != null ? MC.player.blockPosition() : null;
        if (playerPos == null) return;

        List<SectionPos> sections = new ArrayList<>();
        SectionPos pos;
        while ((pos = computeQueue.poll()) != null) {
            sections.add(pos);
        }

        sections.sort(Comparator.comparingDouble(a -> DistanceUtil.getDistanceSquared(a, playerPos)));
        computeQueue.addAll(sections);
    }

    @Override
    public void update(OverlayManagerUpdateData data) {
        if (MC.level == null) return;
        removeOldEntries();
        if (!data.active()) {
            active = false;
            clearAll();
            return;
        }
        int requiredSections = getRequiredSections(data.chunkScanRadius());
        if (requiredSections > maxCacheSize) {
            updateMaxCacheSize(requiredSections);
            OverlayLib.LOGGER.info("Resizing maxCacheSize, total sections * 2 ({}) is higher than current limit {}. New size is {}", requiredSections, maxCacheSize, requiredSections);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    private static int getRequiredSections(int chunkScanRadius) {
        int horizontalChunks = 0;
        for (int dx = -chunkScanRadius; dx <= chunkScanRadius; dx++) {
            for (int dz = -chunkScanRadius; dz <= chunkScanRadius; dz++) {
                if (dx * dx + dz * dz <= chunkScanRadius * chunkScanRadius) {
                    horizontalChunks++;
                }
            }
        }
        int verticalSections = MC.level.getMaxSectionY() - MC.level.getMinSectionY() + 1;
        int totalSections = horizontalChunks * verticalSections;
        return totalSections * 2;
    }

    public void updateMaxCacheSize(int newMaxCacheSize) {
        if (newMaxCacheSize < 1) return;
        this.maxCacheSize = newMaxCacheSize;
        removeOldEntries();
    }

    public void removeOldEntries() {
        removeOldEntries(maxCacheSize);
    }

    public void removeOldEntries(int targetSize) {
        if (targetSize < 0) return;
        if (cache.size() + 16 < targetSize) return;
        cache.values().stream()
                .sorted(Comparator.comparingLong(CacheSectionPosEntry::lastAccessTime))
                .limit(cache.size() / 5)
                .forEach(entry -> cache.remove(entry.pos()));
    }

    private void compute(SectionPos sectionPos) {
        if (MC.level == null || MC.player == null) return;
        if (computeFunction == null) return;
        if (cache.size() >= maxCacheSize) return;

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

        OverlayRendererBlockData[] blocksArray = renderableBlocks.toArray(new OverlayRendererBlockData[0]);
        cache.put(sectionPos, new CacheSectionPosEntry(sectionPos, blocksArray, System.currentTimeMillis()));
    }

    public void clear(SectionPos sectionPos) {
        cache.remove(sectionPos);
        queuedSections.remove(sectionPos);
        compute(sectionPos);
    }

    public void clearFromBlockPos(BlockPos blockPos) {
        SectionPos sectionPos = SectionPos.of(blockPos);
        clear(sectionPos);
    }

    public void clearAll() {
        cache.clear();
        computeQueue.clear();
        queuedSections.clear();
    }

    private static void ensureTickRegistered() {
        if (tickRegistered) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> ACTIVE_CACHES.forEach(CachedOverlayManager::processQueue));
        tickRegistered = true;
    }
}

