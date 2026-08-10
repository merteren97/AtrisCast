# alpha11 review checklist

- [ ] Unit tests pass.
- [ ] Android lint passes.
- [ ] Debug APK assembles.
- [ ] FairPlay JNI libraries are packaged for all configured ABIs.
- [ ] Background mirror permission prompt opens the Android system setting.
- [ ] Mirroring starts from the TV launcher without manually reopening AtrisCast after permission is granted.
- [ ] Mirror stops cleanly and removes the overlay surface.
- [ ] Rapid scrolling does not accumulate noticeable delay.
- [ ] Portrait/landscape transitions do not repeatedly rebuild or blank the decoder.
- [ ] Audio remains single, stable, and approximately synchronized during a 5-10 minute playback test.
