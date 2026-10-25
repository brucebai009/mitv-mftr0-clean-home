package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class WallpaperRemovalPlanTest {
 private WallpaperCandidate video(String id, String poster) { return new WallpaperCandidate(id,id,WallpaperCandidate.Type.VIDEO,"videos/"+id+".mp4",poster,"",3840,2160,60,10000,"",Collections.emptyList()); }
 private WallpaperCandidate image(String path) { return new WallpaperCandidate("image", "image",WallpaperCandidate.Type.IMAGE,path,"","",0,0,0,0,"",Collections.emptyList()); }
 @Test public void staticChoicesRemovedButSharedVideoPostersRetained() {
  WallpaperRemovalPlan p=WallpaperRemovalPlan.create(Arrays.asList(video("good","wallpapers/shared.jpg"), image("wallpapers/shared.jpg"), image("wallpapers/static.png")),true,Collections.emptySet(),"good","videos/good.mp4","videos/good.mp4");
  assertEquals(1,p.keep.size()); assertFalse(p.paths.contains("wallpapers/shared.jpg")); assertTrue(p.paths.contains("wallpapers/static.png"));
 }
 @Test public void exactVideoRemovalDoesNotTouchAnotherVideoOrItsPoster() {
  WallpaperRemovalPlan p=WallpaperRemovalPlan.create(Arrays.asList(video("bad","wallpapers/shared.jpg"),video("bad-2","wallpapers/shared.jpg")),false,Collections.singleton("bad"),"bad-2","videos/bad-2.mp4", "");
  assertEquals(Collections.singleton("videos/bad.mp4"),p.paths); assertEquals("bad-2",p.keep.get(0).id);
 }
 @Test(expected=IllegalStateException.class) public void activeOldPlayerIsProtectedEvenAfterPreferenceChanged() {
  WallpaperRemovalPlan.create(Arrays.asList(video("bad",""),video("good","")),false,Collections.singleton("bad"),"good","videos/good.mp4","videos/bad.mp4");
 }
 @Test public void legacyVideoFallbackFrameIsPreserved() {
  WallpaperRemovalPlan p=WallpaperRemovalPlan.create(Arrays.asList(video("good",""),image("wallpaper.jpg")),true,Collections.emptySet(),"good","videos/good.mp4", "");
  assertTrue(p.paths.isEmpty()); assertTrue(p.protectedPosters.contains("wallpaper.jpg"));
 }
}
