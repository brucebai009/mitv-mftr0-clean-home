package com.gathin.tvhome;

/** Pixel geometry regression; device focus and rendering still require remote testing. */
public final class DockViewportPolicyTest {
    @org.junit.Test public void animateOnlyIntentionalBoundaryNavigation() {
        for (int columns=4; columns<=7; columns++) {
            org.junit.Assert.assertTrue(DockViewportPolicy.animateBoundary(true,0,columns,0,columns,true,false,false));
            org.junit.Assert.assertTrue(DockViewportPolicy.animateBoundary(true,0,columns,columns,0,true,false,false));
            org.junit.Assert.assertFalse(DockViewportPolicy.animateBoundary(true,0,columns,1,2,true,false,false));
            org.junit.Assert.assertFalse(DockViewportPolicy.animateBoundary(true,0,columns,columns,columns*2,true,false,false));
            org.junit.Assert.assertFalse(DockViewportPolicy.animateBoundary(true,0,columns,0,columns,true,true,false));
            org.junit.Assert.assertFalse(DockViewportPolicy.animateBoundary(true,0,columns,0,columns,false,false,false));
            org.junit.Assert.assertFalse(DockViewportPolicy.animateBoundary(true,0,columns,0,columns,true,false,true));
        }
    }
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Check " + checks);
    }
    @org.junit.Test public void dockNeverReanchorsLowerRowsOrAnActiveScroll() {
        for (int padding = 0; padding <= 1080; padding += 3) {
            for (int top = 0; top <= 1080; top += 7) {
                check(padding + DockViewportPolicy.offsetFromPadding(top, padding) == top);
            }
        }
        for (int columns = 4; columns <= 7; columns++) {
            for (int position = 0; position < 50; position++) {
                boolean dock = position < columns;
                check(DockViewportPolicy.shouldReanchor(true, 0, position, columns,
                        false, false, 100, 600) == dock);
                check(!DockViewportPolicy.shouldReanchor(true, 0, position, columns,
                        true, false, 100, 600));
                check(!DockViewportPolicy.shouldReanchor(true, 0, position, columns,
                        false, true, 100, 600));
                check(!DockViewportPolicy.shouldReanchor(true, 0, position, columns,
                        false, false, 600, 600));
                check(!DockViewportPolicy.shouldReanchor(true, 1, position, columns,
                        false, false, 100, 600));
                check(DockViewportPolicy.targetTop(true, 0, position, columns, 600, 18)
                        == (dock ? 600 : 18));
            }
        }
        check(!DockViewportPolicy.shouldReanchor(true, 0, -1, 4, false, false, 0, 600));
        check(!DockViewportPolicy.shouldReanchor(false, 0, 0, 4, false, false, 0, 600));
        check(!DockViewportPolicy.shouldReanchor(true, 0, 0, 4, false, false, 601, 600));
        System.out.println("PASS: " + checks + " geometry/guard assertions (not device tests)");
    }
}
