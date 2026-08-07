package com.luoye.dpt.task;

import com.luoye.dpt.config.Const;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author luoyesiqiu
 */
public class ThreadPool {

    /**
     * Dex processing is memory hungry (dexlib2 loads a whole dex per task).
     * Any parallelism lets several large dexes be held in memory at once and
     * OOMs the app on big APKs. Run dex extraction strictly serially so the
     * peak memory is bounded by a single dex; a 100MB APK typically contains
     * multi-megabyte dex files whose dexlib2 in-memory footprint is ~5x larger.
     */
    private static final int CORE_POOL_SIZE = 1;
    private static final int MAX_POOL_SIZE = 1;

    private static final ThreadPool sInst = new ThreadPool();

    private ExecutorService sThreadPoolExecutor;

    private ThreadPool(){
        initExecutor();
    }

    private void initExecutor() {
        sThreadPoolExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new CustomThreadFactory());
    }

    /**
     * Reset the executor after a protect pass.
     *
     * The pool is shut down at the end of dex extraction to release threads, but a
     * shutdown pool rejects new work. Re-creating the executor on demand makes the
     * pool usable again for the next reinforcement (or the next dex round) instead
     * of throwing RejectedExecutionException.
     */
    public synchronized void reset() {
        shutdown();
        initExecutor();
    }

    public static ThreadPool getInstance() {
        return sInst;
    }

    public static class CustomThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(Const.DEFAULT_THREAD_NAME + "-" + t.getId());
            return t;
        }
    }

    public void execute(Runnable task){
        if (sThreadPoolExecutor.isShutdown()) {
            initExecutor();
        }
        sThreadPoolExecutor.execute(task);
    }

    public void shutdown(){
        sThreadPoolExecutor.shutdown();
    }

}
