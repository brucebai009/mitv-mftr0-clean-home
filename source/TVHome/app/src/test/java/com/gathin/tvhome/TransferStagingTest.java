package com.gathin.tvhome;
import org.junit.Test;
import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
public class TransferStagingTest {
    @Test public void damagedKnownStagingCanBeResumed() throws Exception {
        Path root = Files.createTempDirectory("transfer-known");
        Files.createDirectories(root.resolve("videos"));
        Files.write(root.resolve("videos/clip.mp4"),new byte[]{0});
        TransferStaging.checkInventory(root.toFile(),Collections.singleton("videos/clip.mp4"));
    }
    @Test(expected=IOException.class) public void unknownFileCannotBeOverwrittenOnResume() throws Exception {
        Path root = Files.createTempDirectory("transfer-unknown");
        Files.write(root.resolve("personal.txt"),new byte[]{1});
        TransferStaging.checkInventory(root.toFile(),Collections.singleton("videos/clip.mp4"));
    }
}
