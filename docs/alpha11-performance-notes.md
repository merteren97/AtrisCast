# alpha11 performance notes

The AirPlay mirror path is local-network transport; it does not require internet/cloud routing. Apparent "network slowness" can therefore also be caused by receiver-side buffering or decoder back-pressure.

alpha11 reduces hidden buffering by keeping the H.264 transport queue short, retrying transient MediaCodec back-pressure in decode order, and suppressing redundant codec-configuration resyncs. It also raises the advertised maximum mirror frame rate to 60 fps and enables Android realtime/low-latency decoder hints when supported.

These changes optimize interaction latency while preserving H.264 reference order. They do not force the iPhone to send 60 fps, and they do not claim that every Google TV decoder can sustain every 1080p60 stream. The sender and Android codec can still choose lower operating points when necessary.
