package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class TransferPolicyTest {
    @Test public void mapsOnlyTheSelectedVideoBelowSourceRoot() {
        String root = "/storage/emulated/0/Android/data/com.gathin.tvhome/files";
        assertEquals("videos/train.mp4", TransferPolicy.relativeVideo(root + "/videos/train.mp4", root));
        assertEquals("", TransferPolicy.relativeVideo("", root));
        String[] bad = {root + "-other/videos/a.mp4", root + "/videos/../a.mp4", root + "/videos/.hidden.mp4", "/sdcard/a.mp4", root + "/videos/a.mp4/extra", root + "/videos/a\\b.mp4"};
        for (String path : bad) {
            try { TransferPolicy.relativeVideo(path, root); fail(path); } catch (IllegalArgumentException expected) { }
        }
    }
}
