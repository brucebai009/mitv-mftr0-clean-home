package com.gathin.tvhome;

/** Pixel geometry only; no Android dependencies or persistent device state. */
final class DockViewportPolicy {
    private DockViewportPolicy() { }

    static boolean animateBoundary(boolean dock, int rows, int columns, int from, int to,
                                   boolean animations, boolean reduced, boolean editing) {
        return dock && rows == 0 && columns > 0 && from >= 0 && to >= 0
                && animations && !reduced && !editing && (from < columns) != (to < columns);
    }

    static int offsetFromPadding(int desiredTop, int paddingTop) {
        // LinearLayoutManager offsets are relative to startAfterPadding.
        return desiredTop - paddingTop;
    }

    static boolean shouldReanchor(boolean dockRow, int rowsBelowDock, int focusedPosition,
                                  int columns, boolean scrolling, boolean focusPending,
                                  int decoratedTop, int paddingTop) {
        return dockRow && rowsBelowDock == 0 && focusedPosition >= 0
                && focusedPosition < columns && !scrolling && !focusPending
                && Math.abs((long) decoratedTop - paddingTop) > 1;
    }

    static int targetTop(boolean dockRow, int rowsBelowDock, int position,
                         int columns, int paddingTop, int compactTop) {
        return dockRow && rowsBelowDock == 0 && position >= columns
                ? compactTop : paddingTop;
    }
}
