# AtrisCast

**AtrisCast** is an open-source, local-first casting receiver for Google TV and Android TV.
The project is part of the **AtrisHub** ecosystem, but the application itself is intentionally standalone: **no AtrisHub account, login, cloud service, telemetry, or remote backend is required to run the receiver.**

> Project status: **early alpha / protocol bring-up**. The current milestone implements the Google TV shell, foreground receiver runtime, AirPlay/RAOP Bonjour discovery, a persistent local receiver identity, and a diagnostic RTSP endpoint on TCP port 7000. Pairing, FairPlay session negotiation, media transport and playback are not complete yet.

## Why AtrisCast?

Many Google TV devices do not expose AirPlay receiving as a platform feature. AtrisCast explores a clean, Android-native receiver architecture focused on local-network discovery, low-latency media pipelines and a TV-first user experience.

The long-term target is a receiver that can be installed directly on a compatible Google TV / Android TV device and discovered from iPhone, iPad and Mac devices on the same LAN.

## Current milestone

- Google TV / Android TV launcher application
- TV-first Jetpack Compose UI
- No login and no cloud dependency
- Android foreground receiver service
- Automatic restart after boot (when enabled and permission is available)
- Persistent locally generated device identity
- `_airplay._tcp` Bonjour / mDNS advertisement
- `_raop._tcp` Bonjour / mDNS advertisement
- Wi-Fi multicast handling for background discovery
- Android 17 local-network permission support
- Diagnostic RTSP server on TCP `7000`
- RTSP `OPTIONS` response and handshake visibility in the UI
- GitHub Actions build, lint and unit-test workflow

## Roadmap

1. **Discovery — in progress**
   - Verify AtrisCast appears in iOS/macOS Screen Mirroring pickers across real Google TV devices.
2. **Protocol information and pairing**
   - `/info`, binary plist, pair-setup, pair-verify and persistent controllers.
3. **Mirroring session**
   - SETUP / RECORD, timing channels, encrypted mirroring stream.
4. **Video**
   - H.264 reassembly and Android `MediaCodec` hardware decode to `Surface`.
5. **Audio**
   - AAC first, ALAC where required, `AudioTrack` output and retransmit support.
6. **Synchronization and resilience**
   - AirPlay clock / NTP, jitter buffers, A/V sync, network handoff and recovery.
7. **TV polish**
   - Pairing PIN, receiver settings, device naming, diagnostics and release packaging.

## Architecture

```text
Apple sender (LAN)
       │
       ├── Bonjour / mDNS
       │     ├── _airplay._tcp
       │     └── _raop._tcp
       │
       └── RTSP / AirPlay session
                 │
        ┌────────▼────────┐
        │    AtrisCast    │
        │   Google TV     │
        ├─────────────────┤
        │ ReceiverService │
        │ Discovery       │
        │ RTSP            │
        │ Pairing*        │
        │ Mirroring*      │
        │ MediaCodec*     │
        │ AudioTrack*     │
        └─────────────────┘

* planned / under development
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the module boundaries and implementation rules.

## Build requirements

- Android Studio with Android 17 / API 37 SDK
- JDK 17
- Gradle 9.5+
- Android Gradle Plugin 9.3.x

This repository intentionally keeps generated binaries out of source control. If you have Gradle installed locally:

```bash
gradle assembleDebug
```

The CI workflow installs Gradle 9.5 and Android API 37 explicitly, then runs tests, lint and a debug APK build.

## Local-only design

AtrisCast's receiver path is designed to stay on your LAN:

- no account authentication
- no AtrisHub API requirement
- no hosted relay
- no analytics SDK
- no remote command channel

AtrisHub remains the product family and project home, not a runtime dependency.

## AtrisHub

AtrisCast is an AtrisHub open-source project.

**AtrisHub:** `https://atrishub.com`

## Compatibility note

AirPlay is a proprietary Apple technology. AtrisCast is an independent interoperability project and is not affiliated with Apple. Protected/DRM media may not be available to third-party receivers. Compatibility can vary by sender OS version and device firmware.

The project does not ship Apple proprietary certificates, keys or firmware.

## License

Apache License 2.0. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).

Before introducing third-party protocol code, cryptographic implementations or native decoders, contributors must verify and preserve the upstream license. Do not import GPL code into the Apache-licensed core without an explicit repository-level licensing decision.

## Turkish

Türkçe README için: [`README_TR.md`](README_TR.md)
