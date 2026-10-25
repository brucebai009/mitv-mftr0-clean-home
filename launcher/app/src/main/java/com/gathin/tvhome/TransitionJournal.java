package com.gathin.tvhome;

import android.util.Log;
import java.util.ArrayDeque;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Bounded process-local event evidence. Sampling may be slow; completed transitions are not lost. */
final class TransitionJournal {
    static final int LIMIT = 64;
    private static final ArrayDeque<JSONObject> events = new ArrayDeque<>();
    private static long sequence;
    private TransitionJournal() { }
    static synchronized long begin(long count, String from, String to, String reason, long eos,
                                   long fadeOut, long black, boolean idle) {
        try {
            JSONObject row = new JSONObject().put("sequence", ++sequence).put("count", count)
                    .put("from", from).put("to", to).put("reason", reason).put("eosAt", eos)
                    .put("fadeOutAt", fadeOut).put("blackAt", black).put("idle", idle);
            events.addLast(row);
            while (events.size() > LIMIT) events.removeFirst();
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        return sequence;
    }
    static synchronized void event(long id, String key, long at) {
        if (id == 0) return;
        for (JSONObject row : events) if (row.optLong("sequence") == id) {
            try { row.put(key, at); } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
            if ("settledAt".equals(key) || "failedAt".equals(key)) Log.i("TvHome.Fade", row.toString());
            return;
        }
    }
    static synchronized JSONArray snapshot() {
        try { return new JSONArray(new JSONArray(events).toString()); }
        catch (JSONException impossible) { throw new IllegalStateException(impossible); }
    }
}
