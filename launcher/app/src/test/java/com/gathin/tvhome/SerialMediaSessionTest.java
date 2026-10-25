package com.gathin.tvhome;

import org.junit.Test;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class SerialMediaSessionTest {
    private static final class QueueWorker implements SerialMediaSession.Dispatcher {
        final Queue<Runnable> tasks = new ArrayDeque<>();
        public void post(Runnable task) { tasks.add(task); }
        void drain() { while (!tasks.isEmpty()) tasks.remove().run(); }
    }
    private static class FakeBackend implements SerialMediaSession.Backend {
        SerialMediaSession.Events events;
        int prepares, starts, releases;
        public void prepare(SerialMediaSession.Events value) { events = value; prepares++; }
        public void start() { starts++; }
        public void release() { releases++; }
    }
    private static final class Listener implements SerialMediaSession.Listener {
        int sizes, frames, failures, completions;
        public void size(int w, int h) { sizes++; }
        public void rendered() { frames++; }
        public void failed() { failures++; }
        public void completed() { completions++; }
    }
    private SerialMediaSession session(SerialMediaSession.Dispatcher worker,
            BackgroundResourceCoordinator resources, FakeBackend backend, Listener listener) {
        return new SerialMediaSession(worker, resources, BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER,
                () -> backend, listener);
    }

    @Test public void cancelledBeforeOpenNeverAllocatesDecoder() {
        QueueWorker worker = new QueueWorker();
        FakeBackend backend = new FakeBackend();
        SerialMediaSession session = session(worker, new BackgroundResourceCoordinator(), backend, new Listener());
        session.open(); session.cancel(); worker.drain();
        assertEquals(0, backend.prepares);
        assertEquals(0, backend.releases);
    }

    @Test public void callbacksAfterCancelCannotStartOrRevealOldVideo() {
        QueueWorker worker = new QueueWorker();
        FakeBackend backend = new FakeBackend();
        Listener listener = new Listener();
        SerialMediaSession session = session(worker, new BackgroundResourceCoordinator(), backend, listener);
        session.open(); worker.drain(); session.cancel();
        // A prepared callback already queued by MediaPlayer can arrive before the disposal runnable.
        backend.events.prepared(); backend.events.size(1920, 1080); backend.events.rendered();
        backend.events.error(); worker.drain(); session.cancel(); worker.drain();
        assertEquals(0, backend.starts);
        assertEquals(0, listener.frames);
        assertEquals(0, listener.sizes);
        assertEquals(0, listener.failures);
        assertEquals(1, backend.releases);
    }

    @Test public void decoderFailureFallsBackOnceAndReleasesLease() {
        QueueWorker worker = new QueueWorker();
        FakeBackend backend = new FakeBackend();
        Listener listener = new Listener();
        BackgroundResourceCoordinator resources = new BackgroundResourceCoordinator();
        SerialMediaSession session = session(worker, resources, backend, listener);
        session.open(); worker.drain(); backend.events.error(); backend.events.error();
        assertEquals(1, listener.failures);
        assertEquals(1, backend.releases);
        assertTrue(resources.acquire(BackgroundResourceCoordinator.Owner.PREVIEW, new Object()));
    }

    @Test public void occupiedLeaseDoesNotCreateASecondDecoder() {
        QueueWorker worker = new QueueWorker();
        BackgroundResourceCoordinator resources = new BackgroundResourceCoordinator();
        Object other = new Object();
        assertTrue(resources.acquire(BackgroundResourceCoordinator.Owner.PREVIEW, other));
        FakeBackend backend = new FakeBackend(); Listener listener = new Listener();
        SerialMediaSession session = session(worker, resources, backend, listener);
        session.open(); worker.drain();
        assertEquals(0, backend.prepares);
        assertEquals(1, listener.failures);
        assertTrue(resources.isOwner(BackgroundResourceCoordinator.Owner.PREVIEW, other));
    }

    @Test public void slowNativeReleaseDoesNotBlockCancelOrOverlapNextDecoder() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch prepared = new CountDownLatch(1);
        CountDownLatch releasing = new CountDownLatch(1);
        CountDownLatch unblockRelease = new CountDownLatch(1);
        CountDownLatch secondPrepared = new CountDownLatch(1);
        BackgroundResourceCoordinator resources = new BackgroundResourceCoordinator();
        FakeBackend first = new FakeBackend() {
            public void prepare(SerialMediaSession.Events e) { super.prepare(e); prepared.countDown(); }
            public void release() {
                releasing.countDown();
                try { if (!unblockRelease.await(3, TimeUnit.SECONDS)) throw new AssertionError("release not unblocked"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                super.release();
            }
        };
        FakeBackend second = new FakeBackend() {
            public void prepare(SerialMediaSession.Events e) { super.prepare(e); secondPrepared.countDown(); }
        };
        SerialMediaSession one = session(worker::execute, resources, first, new Listener());
        SerialMediaSession two = session(worker::execute, resources, second, new Listener());
        try {
            one.open(); assertTrue(prepared.await(2, TimeUnit.SECONDS));
            one.cancel(); // Must return while the simulated native release remains blocked.
            assertTrue(releasing.await(2, TimeUnit.SECONDS));
            two.open();
            assertFalse(resources.acquire(BackgroundResourceCoordinator.Owner.PREVIEW, new Object()));
            assertEquals(1, secondPrepared.getCount());
            unblockRelease.countDown();
            assertTrue(secondPrepared.await(2, TimeUnit.SECONDS));
            assertEquals(1, first.releases);
            two.cancel();
        } finally {
            unblockRelease.countDown(); worker.shutdown();
            assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test public void prepareExceptionDisposesPartialBackendAndKeepsUiFallback() {
        QueueWorker worker = new QueueWorker(); Listener listener = new Listener();
        FakeBackend backend = new FakeBackend() {
            public void prepare(SerialMediaSession.Events e) { throw new IllegalStateException("vendor setup failed"); }
        };
        SerialMediaSession session = session(worker, new BackgroundResourceCoordinator(), backend, listener);
        session.open(); worker.drain();
        assertEquals(1, backend.releases);
        assertEquals(1, listener.failures);
    }

    @Test public void completionIsOnceAndDoesNotBecomeFailureOrLateRender() {
        QueueWorker worker = new QueueWorker(); FakeBackend backend = new FakeBackend();
        Listener listener = new Listener();
        SerialMediaSession session = session(worker, new BackgroundResourceCoordinator(), backend, listener);
        session.open(); worker.drain(); backend.events.prepared();
        backend.events.completed(); backend.events.completed(); backend.events.error(); backend.events.rendered();
        assertEquals(1, listener.completions); assertEquals(0, listener.failures); assertEquals(0, listener.frames);
        session.cancel(); worker.drain(); assertEquals(1, backend.releases);
    }

    @Test public void continuationRegisteredAfterFailureStillRunsAfterRelease() {
        QueueWorker worker = new QueueWorker(); FakeBackend backend = new FakeBackend();
        BackgroundResourceCoordinator resources = new BackgroundResourceCoordinator();
        SerialMediaSession session = session(worker, resources, backend, new Listener());
        session.open(); worker.drain(); backend.events.error();
        int[] calls = {0};
        session.cancel(() -> {
            assertEquals(1, backend.releases);
            assertTrue(resources.acquire(BackgroundResourceCoordinator.Owner.PREVIEW, new Object()));
            calls[0]++;
        });
        assertEquals(0, calls[0]); worker.drain(); assertEquals(1, calls[0]);
        assertEquals(1, backend.releases);
    }

    @Test public void repeatedCancelRegistrationsDoNotOverwriteReleaseContinuations() {
        QueueWorker worker = new QueueWorker(); FakeBackend backend = new FakeBackend();
        SerialMediaSession session = session(worker, new BackgroundResourceCoordinator(), backend, new Listener());
        session.open(); worker.drain();
        int[] calls = {0};
        session.cancel(() -> { assertEquals(1, backend.releases); calls[0]++; });
        session.cancel(() -> { assertEquals(1, backend.releases); calls[0]++; });
        worker.drain(); assertEquals(2, calls[0]); assertEquals(1, backend.releases);
    }
}
