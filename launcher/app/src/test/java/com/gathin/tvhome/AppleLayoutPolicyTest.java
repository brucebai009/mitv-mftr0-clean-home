package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppleLayoutPolicyTest {
    @Test public void cardsAreSixteenByTen() {
        assertEquals(100, AppleLayoutPolicy.cardHeightDp(160));
        assertEquals(125, AppleLayoutPolicy.cardHeightDp(200));
    }

    @Test public void dockIsCappedAtNinetyThreePercent() {
        assertEquals(930, AppleLayoutPolicy.dockWidthPx(1000));
        assertEquals(1488, AppleLayoutPolicy.dockWidthPx(1600));
    }

    @Test public void supportedColumnChoicesMatchSettings() {
        for (int columns = 4; columns <= 7; columns++) assertTrue(AppleLayoutPolicy.isColumnChoice(columns));
        assertFalse(AppleLayoutPolicy.isColumnChoice(3));
        assertFalse(AppleLayoutPolicy.isColumnChoice(8));
    }
}
