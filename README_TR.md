# AtrisCast

**AtrisCast**, Google TV ve Android TV için geliştirilen açık kaynaklı, local-first bir casting receiver projesidir.
Proje **AtrisHub** ekosisteminin bir parçasıdır; fakat uygulama bilinçli olarak bağımsız tasarlanır: **AtrisHub hesabı, giriş ekranı, cloud servis, telemetri veya uzak backend çalışması için gerekli değildir.**

> Proje durumu: **erken alpha / protokol geliştirme aşaması**. Şu anki milestone Google TV uygulama iskeletini, foreground receiver servisini, AirPlay/RAOP Bonjour keşfini, kalıcı local cihaz kimliğini ve TCP 7000 üzerindeki teşhis amaçlı RTSP endpoint'ini içerir. Pairing, FairPlay session, medya aktarımı ve oynatma henüz tamamlanmamıştır.

## Neden AtrisCast?

Birçok Google TV cihazı platform seviyesinde AirPlay receiver özelliği sunmuyor. AtrisCast; Android'in kendi ağ ve medya altyapısını kullanan, local ağ üzerinde çalışan, düşük gecikmeye odaklı ve TV kumandasına uygun bir receiver mimarisi oluşturmayı hedefliyor.

Uzun vadeli hedef, uyumlu bir Google TV / Android TV cihazına doğrudan kurulabilen ve aynı yerel ağdaki iPhone, iPad ve Mac cihazlarından görülebilen bir receiver oluşturmaktır.

## Şu anki milestone

- Google TV / Android TV launcher uygulaması
- TV için Jetpack Compose arayüzü
- Login ve cloud bağımlılığı yok
- Android foreground receiver service
- İzin mevcut olduğunda boot sonrası otomatik başlatma
- Local olarak üretilen kalıcı cihaz kimliği
- `_airplay._tcp` Bonjour / mDNS yayını
- `_raop._tcp` Bonjour / mDNS yayını
- Background discovery için Wi-Fi multicast yönetimi
- Android 17 local-network permission desteği
- TCP `7000` üzerinde teşhis amaçlı RTSP server
- RTSP `OPTIONS` cevabı ve handshake bilgisinin UI'da gösterilmesi
- GitHub Actions build, lint ve unit test workflow'u

## Yol haritası

1. **Discovery — geliştiriliyor**
   - Gerçek Google TV cihazlarında iOS/macOS Screen Mirroring listesinde AtrisCast'in görünmesini doğrulamak.
2. **Protocol info ve pairing**
   - `/info`, binary plist, pair-setup, pair-verify ve kalıcı controller kayıtları.
3. **Mirroring session**
   - SETUP / RECORD, timing kanalları ve şifreli mirroring stream.
4. **Video**
   - H.264 paket birleştirme ve Android `MediaCodec` ile donanım decode.
5. **Ses**
   - Önce AAC, gerektiğinde ALAC, `AudioTrack` ve retransmit desteği.
6. **Senkronizasyon ve kararlılık**
   - AirPlay clock / NTP, jitter buffer, A/V sync, ağ değişimi ve recovery.
7. **TV polish**
   - Pairing PIN, receiver ayarları, cihaz ismi, diagnostics ve release paketleme.

## Mimari

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

* planlanan / geliştirilmekte olan katmanlar
```

Detaylı mimari için [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) dosyasına bakabilirsin.

## Build gereksinimleri

- Android 17 / API 37 SDK içeren Android Studio
- JDK 17
- Gradle 9.5+
- Android Gradle Plugin 9.3.x

Local sisteminde Gradle varsa:

```bash
gradle assembleDebug
```

CI workflow'u Gradle 9.5 ve Android API 37'yi açıkça kurar; ardından unit test, lint ve debug APK build çalıştırır.

## Tamamen local çalışma yaklaşımı

AtrisCast receiver yolu LAN içinde kalacak şekilde tasarlanır:

- kullanıcı hesabı yok
- AtrisHub API zorunluluğu yok
- hosted relay yok
- analytics SDK yok
- dışarıdan remote command kanalı yok

AtrisHub, ürün ailesi ve proje bağlantısıdır; runtime bağımlılığı değildir.

## AtrisHub

AtrisCast bir AtrisHub açık kaynak projesidir.

**AtrisHub:** `https://atrishub.com`

## Uyumluluk notu

AirPlay Apple'a ait proprietary bir teknolojidir. AtrisCast bağımsız bir interoperability projesidir ve Apple ile bağlantılı değildir. DRM/protected içerikler üçüncü taraf receiver'larda çalışmayabilir. Uyumluluk iOS/macOS sürümüne ve TV firmware'ine göre değişebilir.

Projede Apple'a ait proprietary sertifika, key veya firmware dağıtılmaz.

## Lisans

Apache License 2.0. Ayrıntılar için [`LICENSE`](LICENSE) ve [`NOTICE`](NOTICE).

Üçüncü taraf protokol kodu, kriptografi implementasyonu veya native decoder eklenmeden önce upstream lisansı doğrulanmalıdır. Repository seviyesinde açık bir lisans kararı olmadan GPL kodu Apache lisanslı core'a taşınmamalıdır.

## English

English README: [`README.md`](README.md)
