package net.lugo.overlaylib;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.core.SectionPos;
import org.lwjgl.system.MemoryUtil;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class SectionMeshCache {
    public static final SectionMesh EMPTY_MESH = new SectionMesh(null, 0, 0);

    public record SectionMesh(GpuBufferSlice slice, int vertexCount, int indexCount) {
        public boolean isEmpty() {
            return vertexCount == 0;
        }
    }

    private final int maxCacheSize;

    private final Map<SectionPos, SectionMeshCache.Entry> entries = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SectionPos, SectionMeshCache.Entry> eldest) {
            if (size() > maxCacheSize) {
                eldest.getValue().close();
                return true;
            }
            return false;
        }
    });

    private static final class Entry {
        SectionMesh mesh;
        long dataVersion = Long.MIN_VALUE;
        long frameToken = Long.MIN_VALUE;
        MappableRingBuffer vertexBuffer;

        void close() {
            if (vertexBuffer != null) {
                vertexBuffer.close();
                vertexBuffer = null;
            }
            mesh = null;
        }
    }

    public SectionMeshCache() {
        this(4096);
    }

    public SectionMeshCache(int maxCacheSize) {
        this.maxCacheSize = Math.max(1, maxCacheSize);
    }

    public boolean isCurrent(SectionPos pos, long dataVersion, long frameToken) {
        Entry entry = entries.get(pos);
        return entry != null && entry.mesh != null
                && entry.dataVersion == dataVersion && entry.frameToken == frameToken;
    }

    public void store(SectionPos pos, long dataVersion, long frameToken, MeshData built) {
        Entry entry = entries.get(pos);
        if (entry == null) {
            entry = new Entry();
            entries.put(pos, entry);
        } else if (entry.vertexBuffer != null) {
            entry.vertexBuffer.rotate();
        }

        if (built == null) {
            entry.mesh = EMPTY_MESH;
        } else {
            entry.mesh = upload(entry, built);
        }
        entry.dataVersion = dataVersion;
        entry.frameToken = frameToken;
    }

    private SectionMesh upload(Entry entry, MeshData built) {
        MeshData.DrawState drawState = built.drawState();
        VertexFormat format = drawState.format();
        int vertexBufferSize = drawState.vertexCount() * format.getVertexSize();

        if (entry.vertexBuffer == null || entry.vertexBuffer.size() < vertexBufferSize) {
            if (entry.vertexBuffer != null) {
                entry.vertexBuffer.currentBuffer();
                entry.vertexBuffer.close();
            }
            entry.vertexBuffer = new MappableRingBuffer(
                    () -> OverlayLib.MOD_ID + " section mesh",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                    vertexBufferSize
            );
        }

        GpuBufferSlice slice = entry.vertexBuffer.currentBuffer().slice(0, built.vertexBuffer().remaining());
        try (GpuBufferSlice.MappedView mappedView = slice.map(false, true)) {
            MemoryUtil.memCopy(built.vertexBuffer(), mappedView.data());
        }
        return new SectionMesh(slice, drawState.vertexCount(), drawState.indexCount());
    }

    public SectionMesh get(SectionPos pos) {
        Entry entry = entries.get(pos);
        return entry == null ? null : entry.mesh;
    }

    public void remove(SectionPos pos) {
        Entry entry = entries.remove(pos);
        if (entry != null) {
            entry.close();
        }
    }

    public void clearAll() {
        synchronized (entries) {
            Iterator<Entry> it = entries.values().iterator();
            while (it.hasNext()) {
                it.next().close();
                it.remove();
            }
        }
    }
}
