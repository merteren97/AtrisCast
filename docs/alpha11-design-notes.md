# alpha11 mirroring design notes

## Latency control

AirPlay H.264 is prediction-based. Dropping an arbitrary P-frame when a local queue fills can invalidate the following reference chain and forces AtrisCast to wait for another IDR. alpha11 therefore keeps the transport queue deliberately small and uses short TCP back-pressure rather than continuously dropping predictive frames.

The hardware decoder no longer has a second large backlog for normal decoder pressure. If MediaCodec temporarily has no input slot, AtrisCast briefly drains output and retries the same frame before consuming another network frame.

Repeated SPS/PPS packets are not automatically treated as a new video epoch. Exact duplicates are ignored, geometry-only updates are propagated without resync, and only a real codec configuration change clears the queue and requests a new keyframe epoch.

## Android decoder and network hints

The H.264 decoder is configured for realtime priority. On Android 11+ the low-latency MediaCodec mode is enabled only when the selected codec advertises support for it. The AirPlay display profile now advertises a 60 fps maximum; this remains advisory and the sender can choose a lower rate.

A Wi-Fi low-latency lock and a bounded partial CPU wake lock are held only while the mirror stream is active, then released on mirror stop/client disconnect/service shutdown.

## Background rendering

A foreground service can continue to advertise and accept AirPlay while MainActivity is not visible, but Android can restrict an Activity launched directly from the background. alpha11 requests the user-controlled Display-over-other-apps permission and, when granted, creates a non-focusable/non-touchable full-screen SurfaceView overlay only for an active background mirror session.

The overlay preserves the sender's visible aspect ratio. Once created, its Surface remains the decoder target until the session ends to avoid a mid-stream MediaCodec target rebuild when MainActivity becomes visible again.
