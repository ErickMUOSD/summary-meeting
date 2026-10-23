package com.cosmicchimps.meetingsummary;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ejecutor sencillo para trabajo en segundo plano con callback en el hilo UI. */
public final class Bg {
    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Work<T> { T run() throws Exception; }
    public interface Ok<T> { void run(T result); }
    public interface Err { void run(Exception e); }

    public static <T> void run(Work<T> work, Ok<T> ok, Err err) {
        POOL.execute(() -> {
            try {
                T result = work.run();
                MAIN.post(() -> ok.run(result));
            } catch (Exception e) {
                MAIN.post(() -> err.run(e));
            }
        });
    }

    public static void onMain(Runnable r) { MAIN.post(r); }
}
