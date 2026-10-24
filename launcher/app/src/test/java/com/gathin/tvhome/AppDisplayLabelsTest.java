package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppDisplayLabelsTest {
    @Test
    public void stockXiaomiHomeUsesTheUnambiguousChineseLabel() {
        assertEquals("小米桌面", AppDisplayLabels.forPackage("com.mitv.tvhome", "Home"));
    }

    @Test
    public void otherAppsKeepTheirPlatformLabel() {
        assertEquals("VLC", AppDisplayLabels.forPackage("org.videolan.vlc", "VLC"));
    }
}
