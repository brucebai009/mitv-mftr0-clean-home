package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScreenSpaceLensTest {
    @Test public void rendersTransparentOutsideRoundedSurface() {
        int[] background = new int[16 * 16];
        for (int i = 0; i < background.length; i++) background[i] = 0xff204060;
        int[] result = ScreenSpaceLens.render(background, 16, 16, 0, 0, 8, 8, 3f, 2f, 0f, 0f);
        assertEquals(0, result[0] >>> 24);
        assertTrue((result[4 * 8 + 4] >>> 24) > 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLensLargerThanBoundedRadius() {
        ScreenSpaceLens.render(new int[16 * 16], 16, 16, 0, 0, 8, 8, 5f, 2f, 0f, 0f);
    }
}
