package com.gathin.tvhome;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DockSelectionTest {
    @Test public void noChoiceUsesExistingThemeButEmptyChoiceStaysEmpty() {
        assertNull(DockSelection.decode(null)); assertTrue(DockSelection.decode("").isEmpty());
    }
    @Test public void saveAndLoadKeepsExplicitOrder() {
        List<String> pins = Arrays.asList("org.vlc", "com.tv", "com.music");
        assertEquals(pins, DockSelection.decode(DockSelection.encode(pins)));
    }
    @Test public void orderEditsDoNotChangeOriginalUntilSaved() {
        List<String> pins = Arrays.asList("com.a", "com.b", "com.c");
        assertEquals(Arrays.asList("com.b", "com.a", "com.c"), DockSelection.move(pins, 1, 0));
        assertEquals("com.a", pins.get(0)); assertEquals(pins, DockSelection.move(pins, 0, -1));
    }
    @Test public void missingAndExtraAppsNeverFillUnselectedSlots() {
        List<String> pins = Arrays.asList("com.a", "com.missing", "com.b");
        assertEquals(Arrays.asList("com.a", "com.b"), DockSelection.visible(pins,
                Arrays.asList("com.b", "com.extra", "com.a"), 4));
    }
    @Test public void reducedColumnCountPreservesOverflowChoice() {
        List<String> pins = Arrays.asList("com.a", "com.b", "com.c", "com.d", "com.e");
        assertEquals(pins.subList(0, 4), DockSelection.visible(pins, pins, 4));
        assertEquals(5, pins.size()); assertEquals(pins, DockSelection.visible(pins, pins, 7));
    }
    @Test public void explicitPinsTakePriorityOverOldGlobalRanks() {
        List<String> pins = Arrays.asList("com.b", "com.a");
        assertTrue(DockSelection.compare(pins, "com.b", "com.a") < 0);
        assertTrue(DockSelection.compare(pins, "com.a", "com.other") < 0);
        assertEquals(0, DockSelection.compare(pins, "com.other", "com.third"));
    }
    @Test public void corruptOrDuplicatedChoiceIsRejected() {
        for (String value : Arrays.asList("com.a,com.a", "com.a,", "../escape", "com.a\ncom.b")) {
            try { DockSelection.decode(value); fail(value); } catch (IllegalArgumentException expected) { }
        }
    }
}
