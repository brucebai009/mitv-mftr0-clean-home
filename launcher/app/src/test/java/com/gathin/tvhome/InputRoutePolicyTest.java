package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class InputRoutePolicyTest {
    private final Set<String> ports = new HashSet<>(Arrays.asList("HW5", "HW6", "HW7", "AV"));
    private String route(String requested, String parent) {
        return InputRoutePolicy.profileKey(requested, parent, ports);
    }
    @Test public void cecChildUsesReportedHdmi3Parent() { assertEquals("HW7", route("PS5-CEC", "HW7")); }
    @Test public void cecChildUsesReportedHdmi1Parent() { assertEquals("HW5", route("AppleTV-CEC", "HW5")); }
    @Test public void cecReceiverUsesReportedHdmi2Parent() { assertEquals("HW6", route("Receiver-CEC", "HW6")); }
    @Test public void directPhysicalPortWorksWithoutParent() { assertEquals("HW5", route("HW5", null)); }
    @Test public void explicitMappingWinsOverParent() { assertEquals("HW6", route("HW6", "HW5")); }
    @Test public void nonHdmiExplicitMappingIsUnchanged() { assertEquals("AV", route("AV", null)); }
    @Test public void unknownInputKeepsGenericFallback() { assertNull(route("Unknown", null)); }
    @Test public void unconfiguredParentIsNotGuessed() { assertNull(route("Unknown", "HW8")); }
    @Test public void selfParentIsNotARoute() { assertNull(route("Unknown", "Unknown")); }
    @Test public void nullRequestDoesNotUseParent() { assertNull(route(null, "HW7")); }
    @Test public void emptyRequestDoesNotUseParent() { assertNull(route("", "HW7")); }
    @Test public void emptyParentIsNotARoute() { assertNull(route("CEC", "")); }
}
