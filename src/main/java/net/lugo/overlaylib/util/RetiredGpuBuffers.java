package net.lugo.overlaylib.util;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.RenderSystem;
import net.lugo.overlaylib.OverlayLib;
import net.minecraft.client.renderer.MappableRingBuffer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public final class RetiredGpuBuffers {
    private static final class Pending {
        final AutoCloseable resource;
        GpuFence fence;

        Pending(AutoCloseable resource) {
            this.resource = resource;
        }
    }

    private static final Deque<Pending> PENDING = new ArrayDeque<>();

    private RetiredGpuBuffers() {
    }

    public static void retire(MappableRingBuffer buffer) {
        if (buffer == null) return;
        retire((AutoCloseable) buffer);
    }

    public static void retire(GpuBuffer buffer) {
        if (buffer == null) return;
        retire((AutoCloseable) buffer);
    }

    private static void retire(AutoCloseable resource) {
        synchronized (PENDING) {
            PENDING.addLast(new Pending(resource));
        }
    }

    public static void onFrameEnd() {
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return;

            GpuFence frameFence = null;
            Iterator<Pending> it = PENDING.iterator();
            while (it.hasNext()) {
                Pending pending = it.next();
                if (pending.fence == null) {
                    if (frameFence == null) {
                        frameFence = RenderSystem.getDevice().createCommandEncoder().createFence();
                    }
                    pending.fence = frameFence;
                } else if (pending.fence.awaitCompletion(0L)) {
                    try {
                        pending.resource.close();
                    } catch (Exception e) {
                        OverlayLib.LOGGER.error("Failed to close retired GPU resource", e);
                    }
                    pending.fence.close();
                    it.remove();
                }
            }
        }
    }
}
