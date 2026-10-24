package com.gathin.tvhome;

import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class ImportedThemeChoicesTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private SharedPreferences preferences(Map<String,Object> values) {
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                    if (method.getName().equals("putString")) values.put((String) args[0], args[1]);
                    if (method.getName().equals("remove")) values.remove((String) args[0]);
                    if (method.getName().equals("commit")) return true;
                    return proxy;
                });
        return (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getString")) return values.getOrDefault(args[0], args[1]);
                    if (method.getName().equals("edit")) return editor;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test public void firstLoadAfterRebasedImportKeepsLayoutAndWallpaperChoices() throws Exception {
        File source = temp.newFolder("source"), target = temp.newFolder("target");
        File original = new File(source, "config.json"), copied = new File(target, "config.json");
        Files.write(original.toPath(), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.copy(original.toPath(), copied.toPath());
        assertTrue(original.setLastModified(100000L)); assertTrue(copied.setLastModified(200000L));
        String sourceSignature=Config.themeSignature(source), targetSignature=Config.themeSignature(target);
        assertNotEquals(sourceSignature,targetSignature);
        Map<String,Object> values = new HashMap<>();
        values.put("columns", 6); values.put(Config.ROWS_BELOW_DOCK_KEY,2);
        values.put(Config.WALLPAPER_CHOICE_KEY,"space_train");
        values.put(Config.THEME_SIGNATURE_KEY,targetSignature);
        Method sync=Config.class.getDeclaredMethod("syncThemeChoices",SharedPreferences.class,File.class,File.class);
        sync.setAccessible(true);
        sync.invoke(null,preferences(values),target,copied);
        assertEquals(6,values.get("columns"));
        assertEquals(2,values.get(Config.ROWS_BELOW_DOCK_KEY));
        assertEquals("space_train",values.get(Config.WALLPAPER_CHOICE_KEY));
        // The old copied source signature really would have erased these same choices.
        values.put(Config.THEME_SIGNATURE_KEY,sourceSignature);
        sync.invoke(null,preferences(values),target,copied);
        assertFalse(values.containsKey("columns"));
    }
}
