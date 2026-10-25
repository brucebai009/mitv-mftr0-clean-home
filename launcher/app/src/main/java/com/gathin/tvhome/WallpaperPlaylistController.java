package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Main-thread playlist coordinator. It owns no decoder: the host creates the existing native or
 * texture player and the serial session releases it before the next source is prepared.
 */
final class WallpaperPlaylistController {
    interface Host {
        WallpaperVideoSession createPlaylistVideo(WallpaperCandidate candidate, boolean looping,
                                                  Runnable completed, Runnable failed);
        void showPlaylistPoster(WallpaperCandidate candidate);
        void removePlaylistVideo(WallpaperVideoSession session);
        void preparePlaylistTransition(WallpaperVideoSession old, Runnable readyToRelease);
        void onPlaylistBindingChanged();
        default void onPlaylistSuspended() { }
    }

    private final Host host;
    private final android.content.Context context;
    private final WallpaperPlaylistStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, WallpaperCandidate> candidates = new HashMap<>();
    private final List<String> ids = new ArrayList<>();
    private final Set<String> failedInRun = new HashSet<>();
    private WallpaperVideoSession session;
    private WallpaperVideoSession retiringSession;
    private WallpaperCandidate current;
    private boolean active;
    private boolean closed;
    private boolean transitioning;

    WallpaperPlaylistController(android.content.Context context, Host host,
                                List<WallpaperCandidate> visible, String fixedChoice) {
        this.host = host;
        this.context = context.getApplicationContext();
        this.store = new WallpaperPlaylistStore(context);
        List<String> saved = WallpaperPlaylistPolicy.normalizeSelection(visible, store.ids());
        for (WallpaperCandidate candidate : visible) {
            if (candidate == null || !candidate.isDynamic()) continue;
            File source = WallpaperCatalog.resolve(Config.dir(context), candidate.relativeFile);
            if (source != null && source.isFile()) candidates.put(candidate.id, candidate);
        }
        for (String id : saved) if (candidates.containsKey(id)) ids.add(id);
        if (ids.isEmpty()) return;
        String preferred = store.currentId();
        if (!candidates.containsKey(preferred) || !ids.contains(preferred)) {
            preferred = ids.contains(fixedChoice) ? fixedChoice : ids.get(0);
            store.setCurrent(preferred, "INITIAL_BIND");
        }
        current = candidates.get(preferred);
        host.onPlaylistBindingChanged();
        File source = current == null ? null : WallpaperCatalog.resolve(Config.dir(context), current.relativeFile);
        if (fixedChoice != null && fixedChoice.equals(preferred) && source != null)
            WallpaperPlaylistStore.seedFixedBookmark(context, preferred, source);
    }

    boolean usable() { return !ids.isEmpty() && current != null; }
    void showInitialPoster() { if (current != null) host.showPlaylistPoster(current); }
    boolean isActive() { return active; }
    String currentId() { return current == null ? "" : current.id; }
    String currentPath(android.content.Context context) {
        if (current == null) return "";
        File source = WallpaperCatalog.resolve(Config.dir(context), current.relativeFile);
        return source == null ? "" : source.getAbsolutePath();
    }
    List<String> ids() { return new ArrayList<>(ids); }

    void start() {
        if (closed || !usable()) return;
        active = true;
        if (session == null && !transitioning) bindCurrent();
        else if (session != null) session.start();
    }

    void stop() {
        active = false;
        if (session != null) session.stop();
        if (retiringSession != null) retiringSession.stop();
    }

    void close() {
        closed = true;
        active = false;
        WallpaperVideoSession old = session;
        session = null;
        if (old != null) old.close();
        if (retiringSession != null) retiringSession.close();
    }

    private void bindCurrent() {
        if (!active || closed || transitioning || current == null) return;
        host.showPlaylistPoster(current);
        final WallpaperCandidate binding = current;
        final boolean looping = availableCount() < 2;
        session = host.createPlaylistVideo(binding, looping,
                () -> onCompleted(binding), () -> onFailed(binding));
        if (session == null) {
            onFailed(binding);
            return;
        }
        session.start();
        store.setCurrent(binding.id, "START");
        host.onPlaylistBindingChanged();
    }

    private void onCompleted(WallpaperCandidate completed) {
        if (closed || session == null || current != completed || !active) return;
        WallpaperPlaylistStore.clearBookmark(context, completed.id);
        String nextId = nextAvailable(completed.id);
        if (nextId.isEmpty()) {
            store.markSuspended(failedInRun.toString());
            host.onPlaylistSuspended();
            return;
        }
        store.markPending(nextId);
        transitionTo(nextId, "NATURAL_EOS");
    }

    private void onFailed(WallpaperCandidate failed) {
        if (closed || transitioning || current != failed) return;
        failedInRun.add(failed.id);
        String nextId = nextAvailable(failed.id);
        if (nextId.isEmpty()) {
            active = false;
            store.markSuspended(failedInRun.toString());
            host.onPlaylistSuspended();
            return;
        }
        transitionTo(nextId, "SKIP_FAILED");
    }

    private String nextAvailable(String from) {
        if (ids.isEmpty()) return "";
        String next = WallpaperPlaylistPolicy.nextId(ids, from);
        for (int i = 0; i < ids.size(); i++) {
            if (!failedInRun.contains(next)) return next;
            next = WallpaperPlaylistPolicy.nextId(ids, next);
        }
        return "";
    }

    private int availableCount() {
        int count = 0;
        for (String id : ids) if (!failedInRun.contains(id)) count++;
        return count;
    }

    private void transitionTo(String nextId, String reason) {
        WallpaperVideoSession old = session;
        String oldId = currentId();
        current = candidates.get(nextId);
        session = null;
        retiringSession = old;
        transitioning = true;
        store.markAdvance(nextId, reason, SystemClock.elapsedRealtime());
        host.onPlaylistBindingChanged();
        Runnable released = () -> main.post(() -> {
            if (old != null) host.removePlaylistVideo(old);
            if (retiringSession == old) retiringSession = null;
            transitioning = false;
            if (!closed && active) bindCurrent();
        });
        if (old != null) host.preparePlaylistTransition(old, () -> old.closeWhenReleased(released));
        else released.run();
        android.util.Log.i("TvHome.Playlist", "advance " + oldId + " -> " + nextId + " reason=" + reason);
    }

}
