package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Ordered package identities; an explicit empty selection is different from theme defaults. */
final class DockSelection {
    static final int MAX = 7;
    static List<String> decode(String text) {
        if (text == null) return null;
        if (text.isEmpty()) return new ArrayList<>();
        List<String> items = new ArrayList<>();
        for (String value : text.split(",", -1)) {
            if (!LibraryApps.validPackage(value) || items.contains(value) || items.size() == MAX)
                throw new IllegalArgumentException("Invalid dock selection");
            items.add(value);
        }
        return items;
    }
    static String encode(List<String> items) {
        String text = String.join(",", items);
        decode(text);
        return text;
    }
    static List<String> move(List<String> items, int from, int to) {
        List<String> next = new ArrayList<>(items);
        if (from >= 0 && from < next.size() && to >= 0 && to < next.size())
            Collections.swap(next, from, to);
        return next;
    }
    static List<String> visible(List<String> saved, List<String> available, int columns) {
        List<String> visible = new ArrayList<>();
        HashSet<String> installed = new HashSet<>(available);
        for (String pkg : saved) {
            if (installed.contains(pkg) && !visible.contains(pkg)) visible.add(pkg);
            if (visible.size() >= columns) break;
        }
        return visible;
    }
    static int compare(List<String> pins, String a, String b) {
        int pa = pins.indexOf(a), pb = pins.indexOf(b);
        if (pa == pb) return 0;
        if (pa < 0) return 1;
        if (pb < 0) return -1;
        return Integer.compare(pa, pb);
    }
}
