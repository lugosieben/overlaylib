package net.lugo.overlaylib.util;

import net.lugo.overlaylib.OverlayLib;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadUtil {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, OverlayLib.MOD_ID + "-worker");
        t.setDaemon(true);
        return t;
    });

    public static void submit(Runnable task) {
        EXECUTOR.submit(task);
    }
}
