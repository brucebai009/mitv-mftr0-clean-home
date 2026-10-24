package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackHealthTest {
    @Test public void advancingFramesNeverTriggerRecoveryEvenWithIdenticalPixels() {
        PlaybackHealth h = new PlaybackHealth(); h.start(0);
        for (long t=1000; t<60000; t+=1000) { h.frame(t); assertEquals(PlaybackHealth.Action.WAIT,h.poll(t)); }
    }
    @Test public void stalledFramesRetryOnceThenFallBack() {
        PlaybackHealth h = new PlaybackHealth(); h.start(0); h.frame(1000);
        assertEquals(PlaybackHealth.Action.WAIT,h.poll(6999));
        assertEquals(PlaybackHealth.Action.RETRY,h.poll(7000));
        assertEquals(PlaybackHealth.Action.WAIT,h.poll(16999));
        assertEquals(PlaybackHealth.Action.FALLBACK,h.poll(17000));
        assertEquals(PlaybackHealth.Action.WAIT,h.poll(999999));
    }
    @Test public void successfulRetryDoesNotResetTheBoundedRecoveryBudget() {
        PlaybackHealth h = new PlaybackHealth(); h.start(0);
        assertEquals(PlaybackHealth.Action.RETRY,h.poll(10000)); h.frame(11000);
        assertEquals(PlaybackHealth.Action.FALLBACK,h.poll(17000));
    }
    @Test public void newForegroundSessionCanPlayAgain() {
        PlaybackHealth h = new PlaybackHealth(); h.start(0); h.poll(10000); h.poll(20000);
        h.start(30000); h.frame(35000); assertEquals(PlaybackHealth.Action.WAIT,h.poll(36000));
    }
}
