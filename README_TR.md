# AtrisCast

**AtrisCast**, Google TV ve Android TV için geliştirilen açık kaynaklı, local-first bir casting receiver projesidir.
Proje **AtrisHub** ekosisteminin bir parçasıdır; fakat uygulama bilinçli olarak bağımsız tasarlanır: **AtrisHub hesabı, giriş ekranı, cloud servis, telemetri veya uzak backend çalışması için gerekli değildir.**

> Proje durumu: **erken alpha / gerçek cihaz protokol doğrulama aşaması**. Alpha05 ile ekran yansıtma için ilk uçtan uca video yolu eklendi: FairPlay session-key işleme, şifreli type-110 mirror paketleri, H.264 çıkarma, Android donanım decoder'ı ve tam ekran Surface rendering. Kod ve CI tarafı hazırlanmıştır; farklı iPhone/iPad/macOS ve Google TV firmware kombinasyonlarında gerçek cihaz doğrulaması devam etmektedir.

## Neden AtrisCast?

Birçok Google TV cihazı platform seviyesinde AirPlay receiver özelliği sunmuyor. AtrisCast; Android'in kendi ağ ve medya altyapısını kullanan, yerel ağ üzerinde çalışan, düşük gecikmeye odaklı ve TV kumandasına uygun bir receiver mimarisi oluşturmayı hedefliyor.

## Mevcut alpha yetenekleri

- Google TV / Android TV launcher uygulaması
- tamamen local foreground receiver service
- `_airplay._tcp` ve `_raop._tcp` keşfi
- donanım MAC adresini dışarı vermeden kalıcı local receiver kimliği
- TCP 7000 üzerinde AirPlay kontrol endpoint'i
- binary-plist `GET /info` yetenek cevabı
- FairPlay `POST /fp-setup` phase 1 / phase 2 görüşmesi
- binary-plist `SETUP` transport görüşmesi
- local UDP timing kanalı
- type-110 mirror TCP paket parser'ı
- ayrı lisanslanan native bridge üzerinden FairPlay session-key decryption
- AES-CTR mirror stream işleme ve AVCC → Annex-B H.264 dönüşümü
- Android `MediaCodec` ile H.264 donanım decode ve canlı `SurfaceView` rendering
- mirror stream başladığında otomatik tam ekran playback görünümü
- Android rendering Surface'i yeniden oluşturduğunda decoder'ın yeniden bağlanması
- alınan byte, render edilen frame, çözünürlük ve decoder hataları için mirror diagnostics
- type-96 audio UDP data/control listener'ları ve type-103 buffered-audio transport bring-up
- `RECORD`, `FLUSH`, `GET_PARAMETER`, `SET_PARAMETER`, `TEARDOWN`, `/feedback` ve `/audioMode` kontrol cevapları
- İngilizce ve Türkçe TV arayüzü; varsayılan dil İngilizce

Ses decode/playback ve daha geniş sender/firmware uyumluluğu halen geliştirme aşamasındadır.

## Build gereksinimleri

- Android Studio / Android SDK 37.1
- JDK 17
- Gradle 9.5+
- Android NDK `27.2.12479018`
- pinned native dependency ile uyumlu Rust toolchain
- `cargo-ndk` 4.1.2

Standart build, değiştirilebilir LGPL FairPlay JNI shared library'sini Android için derler. Build sırasında sabitlenmiş `shairplay-rust` revision'ı alınır ve ilgili lisans metinleri APK içine paketlenir. Sadece protokol/UI geliştirmesi yapılırken şifreli mirroring bilinçli olarak kapatılmak istenirse:

```bash
gradle assembleDebug -PskipFairPlayNative=true
```

Bu flag ile oluşturulan build AirPlay transport görüşmesini yapabilir ancak şifreli ekran yansıtma videosunu çözüp gösteremez.

## Geliştirme durumu

AtrisCast henüz production-ready bir AirPlay receiver değildir. Uyumluluk gerçek Apple cihazlarından gelen handshake ve media akışları üzerinden aşamalı olarak geliştirilmektedir. Tanılama ekranı en son protokol/video aşamasını takip ederek gerçek cihaz regresyonlarını ayırmayı kolaylaştırır.

## Gizlilik

AtrisCast tamamen yerel ağda çalışacak şekilde tasarlanır. AtrisHub login veya cloud bağlantısı gerektirmez ve casting trafiğini AtrisHub backend'ine göndermez.

## Lisanslama

AtrisCast'in Android/Kotlin uygulaması Apache License 2.0 ile lisanslanır. FairPlay JNI bridge, değiştirilebilir ayrı bir native component olarak tutulur ve sabitlenmiş `shairplay-rust` dependency'sinden LGPL-3.0-or-later kod içerir. Kesin sınırlar ve revision için `THIRD_PARTY_NOTICES.md` dosyasına bakın.

## Proje

AtrisCast, AtrisHub ekosistemindeki bağımsız bir açık kaynak projedir.

- Web sitesi: `atrishub.com`
- Lisans: Apache License 2.0 (`LICENSE`)
- Üçüncü taraf bildirimleri: `THIRD_PARTY_NOTICES.md`

AirPlay, iPhone, iPad, Mac ve Apple TV Apple Inc. ticari markalarıdır. AtrisCast Apple Inc. ile bağlantılı, Apple tarafından onaylanmış veya sponsor edilmiş değildir.

## English

English README: [`README.md`](README.md)
