# alpha11 summary

AtrisCast alpha11 targets two issues observed after the alpha10 AirPlay quality/audio fixes:

1. interactive mirroring could feel slower and show intermittent glitches;
2. the receiver service remained discoverable in the background, but the TV picture did not appear until MainActivity was opened.

The update removes large receiver-side frame backlogs, avoids redundant SPS/PPS resyncs, retries MediaCodec back-pressure in-order, uses realtime/low-latency decoder hints when supported, advertises up to 60 fps, holds streaming performance locks only during the mirror session, and adds an Android-approved user-controlled background render surface path.

Version: `0.1.0-alpha11` (`versionCode 11`).
