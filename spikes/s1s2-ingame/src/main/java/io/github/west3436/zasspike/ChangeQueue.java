package io.github.west3436.zasspike;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The enqueue-only side of the change hook: a growable long[] of packed
 * positions on the server thread, and a concurrent fallback for writes that
 * arrive from any other thread.
 */
public final class ChangeQueue {
    public static final ChangeQueue CHUNK = new ChangeQueue();
    public static final ChangeQueue POI = new ChangeQueue();

    private long[] buf = new long[1024];
    private int size;
    private final ConcurrentLinkedQueue<Long> offThread = new ConcurrentLinkedQueue<>();
    public long total;
    public long offThreadTotal;

    public void enqueue(ServerLevel level, BlockPos pos) {
        if (level.getServer().isSameThread()) {
            if (size == buf.length) {
                buf = Arrays.copyOf(buf, size * 2);
            }
            buf[size++] = pos.asLong();
            total++;
        } else {
            offThread.add(pos.asLong());
            offThreadTotal++;
        }
    }

    /** Coalesces the queued positions (GEO-012); returns how many were distinct. */
    public int drainDistinct() {
        long[] a = Arrays.copyOf(buf, size);
        Arrays.sort(a);
        int distinct = 0;
        for (int i = 0; i < a.length; i++) {
            if (i == 0 || a[i] != a[i - 1]) {
                distinct++;
            }
        }
        size = 0;
        offThread.clear();
        return distinct;
    }

    public void clear() {
        size = 0;
        offThread.clear();
    }
}
