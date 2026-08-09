# Third-Party Notices

AtrisCast uses the following third-party software and protocol references in addition to the AndroidX/Kotlin libraries supplied through Gradle.

## dd-plist

- Maven: `com.googlecode.plist:dd-plist:1.29`
- Purpose: read/write Apple binary property-list (`bplist`) payloads used by AirPlay negotiation
- License: MIT
- Project: `3breadt/dd-plist`

## PhairPlay protocol reference

- Project: `mazer666/PhairPlay`
- Purpose: reference behavior for the AirPlay `fp-setup` phase-1/phase-2 negotiation
- License: Apache License 2.0 for the referenced Kotlin protocol layer

AtrisCast does **not** include the GPL PlayFair/libplayfair native stream-key decryption implementation in this build. FairPlay setup negotiation and stream-key decryption are intentionally kept separate so license boundaries remain explicit.

Copyright notices and license terms remain with their respective authors. AtrisCast does not include proprietary Apple keys, certificates, firmware, or copied proprietary Apple source code.
