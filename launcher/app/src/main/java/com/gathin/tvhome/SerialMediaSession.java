package com.gathin.tvhome;

/**
 * One cancellable decoder lease. Every backend call and callback belongs to the supplied serial
 * dispatcher. cancel() only invalidates callbacks and queues disposal: it never waits for a codec.
 * Keep the lease until release actually returns, including when a vendor release call is slow.
 */
final class SerialMediaSession {
    interface Dispatcher { void post(Runnable task); }
    interface Factory { Backend create(); }
    interface Backend {
        void prepare(Events events) throws Exception;
        void start();
        void release();
    }
    interface Events {
        void prepared();
        void size(int width, int height);
        void rendered();
        void error();
    }
    interface Listener {
        void size(int width, int height);
        void rendered();
        void failed();
    }

    private final Dispatcher worker;
    private final BackgroundResourceCoordinator resources;
    private final BackgroundResourceCoordinator.Owner role;
    private final Factory factory;
    private final Listener listener;
    private volatile boolean cancelled;
    // Worker-thread state only. Never hold a monitor while invoking the native backend.
    private boolean released, ownsLease, failed;
    private Backend backend;

    SerialMediaSession(Dispatcher worker, BackgroundResourceCoordinator resources,
                       BackgroundResourceCoordinator.Owner role, Factory factory, Listener listener) {
        this.worker = worker;
        this.resources = resources;
        this.role = role;
        this.factory = factory;
        this.listener = listener;
    }

    void open() { worker.post(this::openOnWorker); }

    void cancel() {
        cancelled = true;
        worker.post(this::releaseOnWorker);
    }

    private boolean live() { return !cancelled && !released && !failed; }

    private void openOnWorker() {
        if (!live()) return;
        if (!resources.acquire(role, this)) { failOnWorker(); return; }
        ownsLease = true;
        try {
            backend = factory.create();
            if (!live()) { releaseOnWorker(); return; }
            backend.prepare(new Events() {
                @Override public void prepared() {
                    if (!live()) { releaseOnWorker(); return; }
                    try { backend.start(); } catch (RuntimeException error) { failOnWorker(); }
                }
                @Override public void size(int width, int height) {
                    if (live()) listener.size(width, height);
                }
                @Override public void rendered() { if (live()) listener.rendered(); }
                @Override public void error() { if (live()) failOnWorker(); }
            });
        } catch (Exception error) { failOnWorker(); }
    }

    private void failOnWorker() {
        if (failed || released) return;
        failed = true;
        // Notify first: a blocked release must not delay the poster fallback on the UI thread.
        if (!cancelled) listener.failed();
        releaseOnWorker();
    }

    private void releaseOnWorker() {
        if (released) return;
        released = true;
        Backend old = backend;
        backend = null;
        try {
            if (old != null) old.release();
        } finally {
            if (ownsLease) resources.release(this);
            ownsLease = false;
        }
    }
}
