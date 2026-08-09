# AtrisCast

**AtrisCast** is an open-source, local-first casting receiver for Google TV and Android TV.
The project is part of the **AtrisHub** ecosystem, but the application itself is intentionally standalone: **no AtrisHub account, login, cloud service, telemetry, or remote backend is required to run the receiver.**

> Project status: **early alpha / protocol bring-up**. The current milestone implements the Google TV product shell, foreground receiver runtime, AirPlay/RAOP Bonjour discovery, persistent local receiver identity, binary-plist capability exchange, FairPlay setup negotiation, and AirPlay transport SETUP with real local timing/mirror/audio sockets. Encrypted stream-key handling and media decode/rendering are still in development.

## Why AtrisCast?

Many Google TV devices do not expose AirPlay receiving as a platform feature. AtrisCast explores a clean, Android-native receiver architecture focused on local-network discovery, low-latency media pipelines and a TV-first user experience.

## Current alpha capabilities

- Google TV / Android TV launcher application
- local-only foreground receiver service
- `_airplay._tcp` and `_raop._tcp` discovery
- persistent local receiver identity without exposing the hardware MAC address
- AirPlay control endpoint on TCP 7000
- binary-plist `GET /info` capability response
- FairPlay `POST /fp-setup` phase 1 / phase 2 negotiation
- binary-plist `SETUP` transport negotiation
- local UDP timing channel
- mirror stream TCP listener (type 110)
- audio UDP data/control listeners (type 96)
- buffered-audio TCP listener bring-up (type 103)
- `RECORD`, `FLUSH`, `GET_PARAMETER`, `SET_PARAMETER`, `TEARDOWN`, `/feedback` and `/audioMode` control acknowledgements
- English and Turkish TV interface, with English as the default
- Home / Settings / Diagnostics TV product UI

The current transport listeners are intentionally a bring-up boundary: they prove the sender can negotiate and open the media paths while the project continues work on FairPlay stream-key processing, media decryption, H.264 decode/rendering and audio playback.

## Development status

AtrisCast is not yet a production-ready AirPlay receiver. Compatibility is being developed incrementally against real Apple-device handshakes. The Diagnostics page is designed to show the latest protocol stage reached during bring-up so regressions can be identified without exposing technical detail on the normal Home screen.

## Privacy

AtrisCast is designed to run entirely on the local network. It does not require an AtrisHub login or cloud connection and does not send casting traffic to an AtrisHub backend.

## Project

AtrisCast is an independent open-source project in the AtrisHub ecosystem.

- Website: `atrishub.com`
- License: Apache License 2.0 (see `LICENSE`)
- Third-party notices: see `THIRD_PARTY_NOTICES.md`

AirPlay, iPhone, iPad, Mac and Apple TV are trademarks of Apple Inc. AtrisCast is not affiliated with, endorsed by, or sponsored by Apple Inc.
