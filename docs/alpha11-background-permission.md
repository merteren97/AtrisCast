# Background mirroring permission

AtrisCast can keep its AirPlay receiver foreground service alive when the TV application UI is not visible. Android may still restrict a background service from bringing an Activity directly to the foreground.

For reliable "select AtrisCast on iPhone and immediately see the picture" behavior while the TV app is backgrounded, alpha11 asks the user once for Android's **Display over other apps** permission. If granted, AtrisCast creates a full-screen SurfaceView overlay only while a mirror session is active and MainActivity is not visible.

The mirror overlay:

- is not touchable;
- is not focusable;
- preserves the sender's visible aspect ratio;
- uses an opaque hardware-accelerated SurfaceView for MediaCodec output;
- is removed as soon as mirroring stops, the AirPlay client disconnects, or the receiver service stops.

If the permission is not granted, AtrisCast keeps the previous best-effort background Activity launch behavior, but Android/Google TV may block that launch. The user can still open AtrisCast manually and the active mirror stream will attach to the normal Activity Surface.
