package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class LauncherAppVisibilityTest {
    @Test public void excludesOnlyExactDuplicateIdentities() {
        for (String pkg : new String[]{"com.ts.launcher", "com.gathin.tvhome", "com.gathin.tvhome.entry", "com.mitv.tvhome"})
            assertTrue(LauncherAppVisibility.hidden(pkg, "com.ts.launcher"));
        for (String pkg : new String[]{"com.gathin.tvhome.other", "com.xiaomi.mitv.settings", "com.xiaomi.voicecontrol", "com.elton.tvmanager", "", "unknown.unlabelled"})
            assertFalse(LauncherAppVisibility.hidden(pkg, "com.ts.launcher"));
    }
    @Test public void managerKeepsItsRecognizableLabel() {
        assertEquals("应用管理", AppDisplayLabels.forPackage("com.elton.tvmanager", ""));
        assertEquals("播放器", AppDisplayLabels.forPackage("org.videolan.vlc", "播放器"));
    }
}
