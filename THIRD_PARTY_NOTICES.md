# Third-Party Notices

AtrisCast uses the following third-party software and protocol references in addition to the AndroidX/Kotlin libraries supplied through Gradle.

## dd-plist

- Maven: `com.googlecode.plist:dd-plist:1.29`
- Purpose: read/write Apple binary property-list (`bplist`) payloads used by AirPlay negotiation
- License: MIT
- Project: `3breadt/dd-plist`

## PhairPlay protocol reference

- Project: `mazer666/PhairPlay`
- Purpose: reference behavior for AirPlay `fp-setup` negotiation and mirror packet framing
- License: Apache License 2.0 for the referenced Kotlin protocol layer
- AtrisCast does not vendor PhairPlay's GPL PlayFair native library into the Apache-licensed Kotlin core.

## shairplay-rust / AtrisCast FairPlay bridge

- Project: `metaneutrons/shairplay-rust`
- Upstream version: `0.7.0`
- Pinned revision: `aaf5025267ba71d6eb5bb631d0b518b7354102a8`
- Purpose: FairPlay session-key decryption used by AirPlay screen mirroring
- Upstream license: GNU Lesser General Public License v3.0 or later (`LGPL-3.0-or-later`)
- AtrisCast integration: compiled into the replaceable `libatriscast_fairplay.so` JNI shared library under `native/fairplay-bridge`; the Android/Kotlin application communicates with it only through `FairPlayNative`.

The build downloads the pinned upstream source into the ignored `native/fairplay-bridge/vendor/` directory and applies a narrowly scoped source-visibility patch so the JNI bridge can call the FairPlay decrypt function. The resulting native library and that modification remain subject to the LGPL-3.0-or-later terms. The bridge source is included in this repository so users can rebuild or replace the shared library with an interface-compatible version.

To avoid maintaining hand-edited license documents, the build packages the exact upstream `shairplay-rust` LGPL license and a standard GNU GPL v3 license text into the APK under `assets/licenses/`. Linux build hosts use their installed canonical GPL v3 copy; other hosts fall back to the revision-pinned SPDX license-list-data copy at `5bf6d9610255540bfbee6890765a616042bf1e11`.

Copyright notices and license terms remain with their respective authors. AtrisCast does not include proprietary Apple keys, certificates, firmware, or copied proprietary Apple source code.
