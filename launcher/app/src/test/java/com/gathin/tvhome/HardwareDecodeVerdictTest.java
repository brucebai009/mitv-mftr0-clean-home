package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class HardwareDecodeVerdictTest {
 @Test public void originalRateAndSizeRequireActualFrames() {
  assertTrue(HardwareDecodeVerdict.pass(3840,2160,60,3840,2160,241,4_000_000_000L,4_000_000L));
  assertTrue(HardwareDecodeVerdict.pass(3840,2160,30,3840,2160,121,4_000_000_000L,4_000_000L));
  assertFalse(HardwareDecodeVerdict.pass(3840,2160,60,3840,2160,121,4_000_000_000L,4_000_000L));
 }
 @Test public void resizedOrClockOnlyOrShortPlaybackIsInsufficient() {
  assertFalse(HardwareDecodeVerdict.pass(3840,2160,60,1920,1080,241,4_000_000_000L,4_000_000L));
  assertFalse(HardwareDecodeVerdict.pass(3840,2160,60,3840,2160,2,4_000_000_000L,4_000_000L));
  assertFalse(HardwareDecodeVerdict.pass(3840,2160,60,3840,2160,121,2_000_000_000L,2_000_000L));
  assertFalse(HardwareDecodeVerdict.pass(3840,2160,60,3840,2160,481,4_000_000_000L,4_000_000L));
 }
}
