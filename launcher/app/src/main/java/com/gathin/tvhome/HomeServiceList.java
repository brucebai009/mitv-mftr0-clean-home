package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/** Preserve every unrelated service, including its original short/long spelling and order. */
final class HomeServiceList {
    private final List<String> entries = new ArrayList<>();
    HomeServiceList(String value) {
        if (value == null || value.isEmpty() || value.equals("null")) return;
        for (String entry : value.split(":", -1)) {
            if (!entry.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+")) {
                throw new IllegalArgumentException("Malformed service list; no write allowed");
            }
            entries.add(entry);
        }
    }
    static String canonical(String entry) {
        int slash = entry.indexOf('/');
        String pkg = entry.substring(0, slash), cls = entry.substring(slash + 1);
        return pkg + "/" + (cls.startsWith(".") ? pkg + cls : cls);
    }
    boolean contains(String own) {
        for (String entry : entries) if (canonical(entry).equals(canonical(own))) return true;
        return false;
    }
    String without(String own) {
        List<String> kept = new ArrayList<>();
        for (String entry : entries) if (!canonical(entry).equals(canonical(own))) kept.add(entry);
        return String.join(":", kept);
    }
    String with(String own) {
        String kept = without(own);
        return kept.isEmpty() ? own : kept + ":" + own;
    }
}
