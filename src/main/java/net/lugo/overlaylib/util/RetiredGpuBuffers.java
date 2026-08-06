package net.lugo.overlaylib.util;

import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.MappableRingBuffer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public final class RetiredGpuBuffers {
    private static final class Pending {
        final MappableRingBuffer buffer;
        GpuFence fence;

        Pending(MappableRingBuffer buffer) {
            this.buffer = buffer;
        }
    }

    private static final Deque<Pending> PENDING = new ArrayDeque<>();

    private RetiredGpuBuffers() {
    }

    public static void retire(MappableRingBuffer buffer) {
        if (buffer == null) return;
        synchronized (PENDING) {
            PENDING.addLast(new Pending(buffer));
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
                    pending.buffer.close();
                    pending.fence.close();
                    it.remove();
                }
            }
        }
    }
}
