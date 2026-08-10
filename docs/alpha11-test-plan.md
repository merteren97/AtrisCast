# AtrisCast alpha11 validation

This checklist targets the two regressions addressed in alpha11: realtime mirroring stability and rendering while the receiver service is backgrounded.

## One-time TV setup

1. Launch AtrisCast after installing alpha11.
2. Grant local-network permission if Android requests it.
3. Grant **Display over other apps** when AtrisCast explains why it is needed. AtrisCast uses the permission only for the full-screen AirPlay render surface while a mirror session is active and the Activity is not visible.

## Background receiver

1. Start the AtrisCast receiver and return to the Google TV launcher or another app.
2. Confirm AtrisCast remains visible in iPhone Screen Mirroring.
3. Start mirroring without reopening AtrisCast.
4. Expected: the iPhone picture appears automatically on the TV.
5. Stop mirroring.
6. Expected: the temporary full-screen mirror surface disappears immediately.

## Realtime / glitch test

1. Start mirroring with AtrisCast open.
2. Rapidly scroll a long page for 60 seconds.
3. Open Control Center several times and swipe between home-screen pages.
4. Rotate the iPhone portrait -> landscape -> portrait at least five times.
5. Play a 5-10 minute video with audio.
6. Expected: no accumulating delay, repeated freeze/resync flashes, duplicated audio, or steadily increasing A/V offset.

## Stress transition

1. While mirroring, leave AtrisCast for the Google TV launcher.
2. Continue interacting with the iPhone for 30 seconds.
3. Return to AtrisCast.
4. Expected: the active mirror remains stable; Surface routing must not force repeated MediaCodec rebuilds.

If a device-specific glitch remains, capture AtrisCast Diagnostics plus the exact Google TV model/Android version and whether the issue occurs on 2.4 GHz, 5 GHz, or Ethernet.
