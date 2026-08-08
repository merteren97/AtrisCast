# AtrisCast Architecture

## Product constraints

AtrisCast is local-first. The receiver must not require AtrisHub authentication, a hosted relay, telemetry, or an internet connection after installation. Internet access may be used by the operating system or package manager for installation/update flows, but casting itself is a LAN operation.

## Module boundaries

### `receiver`
Owns Android lifecycle concerns: foreground service, boot behavior, local-network permission, multicast lock, runtime state, persistent local settings and device identity.

### `airplay.discovery`
Currently represented by `MdnsAdvertiser`. It owns DNS-SD registration only and must not grow pairing or media responsibilities.

### `airplay.rtsp`
`AirPlaySocketServer`, `RtspRequest` and `RtspRequestParser` own the TCP/RTSP framing boundary. The current implementation is diagnostic and returns `501` for protocol methods that are not implemented yet.

### Future `airplay.pairing`
Will own pair-setup, pair-verify, persistent controller keys and PIN policy. Crypto primitives must be isolated behind interfaces and have explicit tests/vectors.

### Future `airplay.mirroring`
Will own SETUP/RECORD session negotiation, encrypted mirror packets, RTP/timing sockets, retransmits and stream lifecycle.

### Future `media.video`
Will transform H.264 access units into Android `MediaCodec` input and render directly to a `Surface`. Do not introduce FFmpeg/GStreamer unless a concrete unsupported-codec requirement justifies it.

### Future `media.audio`
Will decode AAC through platform codecs where possible and output PCM through `AudioTrack`. Native ALAC should be introduced as a small, separately licensed module only if required.

## State model

```text
STOPPED
  ↓
STARTING
  ├── PERMISSION_REQUIRED
  ├── ERROR
  ↓
ADVERTISING
  ↓
CLIENT_CONNECTED
  ↓
ADVERTISING
```

Later protocol work will extend this with `PAIRING`, `NEGOTIATING`, `BUFFERING`, `STREAMING` and recoverable error states.

## Security rules

- Bind only the ports needed by the receiver.
- Treat all LAN clients as untrusted input.
- Apply strict header/body limits before parsing.
- Never log key material, pairing secrets or decrypted payloads.
- Persist only the minimum controller identity required for pairing.
- Do not expose an HTTP admin service or remote debug port in release builds.
- Do not copy proprietary Apple binaries, keys, certificates or firmware.
- Review every third-party license before source or native code enters the tree.

## First-device acceptance test

1. Install debug APK on Google TV / Android TV.
2. Grant local-network permission on Android 17+.
3. Confirm UI reaches `READY`.
4. Confirm TCP 7000 is listening.
5. From iPhone/iPad/Mac on the same LAN, open the AirPlay/Screen Mirroring picker.
6. Confirm the advertised AtrisCast name appears.
7. Select it.
8. Confirm the AtrisCast UI changes to `SENDER CONNECTED` and shows at least one incoming RTSP request.

At this milestone, a full mirror session is **not** expected to start yet.
