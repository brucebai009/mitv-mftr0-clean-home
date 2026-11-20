package com.gathin.tvhome;

import java.util.Set;

/** Exact device-profile keys only. A CEC child inherits its reported physical parent route. */
final class InputRoutePolicy {
    static String profileKey(String requested, String parent, Set<String> known) {
        if (requested == null || requested.isEmpty()) return null;
        if (known.contains(requested)) return requested;
        if (parent != null && !parent.isEmpty() && !parent.equals(requested) && known.contains(parent)) return parent;
        return null;
    }
}
