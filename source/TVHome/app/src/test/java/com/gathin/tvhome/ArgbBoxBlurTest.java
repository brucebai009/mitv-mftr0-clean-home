package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ArgbBoxBlurTest {
    @Test public void zeroRadiusKeepsPixelsAndBlurChangesEdge() {
        int[] source = {0xff000000, 0xffffffff, 0xffffffff, 0xff000000};
        assertEquals(source[0], ArgbBoxBlur.blur(source, 2, 2, 0, 1)[0]);
        assertNotEquals(source[0], ArgbBoxBlur.blur(source, 2, 2, 1, 1)[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOversizedInput() {
        ArgbBoxBlur.blur(new int[1_048_577], 1_048_577, 1, 1, 1);
    }
}
