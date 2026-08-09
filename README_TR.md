# AtrisCast

**AtrisCast**, Google TV ve Android TV için geliştirilen açık kaynaklı, local-first bir casting receiver projesidir.
Proje **AtrisHub** ekosisteminin bir parçasıdır; fakat uygulama bilinçli olarak bağımsız tasarlanır: **AtrisHub hesabı, giriş ekranı, cloud servis, telemetri veya uzak backend çalışması için gerekli değildir.**

> Proje durumu: **erken alpha / protokol geliştirme aşaması**. Mevcut milestone; Google TV ürün arayüzünü, foreground receiver runtime'ını, AirPlay/RAOP Bonjour keşfini, kalıcı yerel receiver kimliğini, binary-plist yetenek alışverişini, FairPlay setup görüşmesini ve gerçek yerel timing/mirror/audio socket'leri açan AirPlay `SETUP` transport katmanını içerir. Şifreli stream key işleme ve medya decode/rendering geliştirme aşamasındadır.

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
- type 110 mirror stream için TCP listener
- type 96 ses için UDP data/control listener'ları
- type 103 buffered audio için TCP listener bring-up
- `RECORD`, `FLUSH`, `GET_PARAMETER`, `SET_PARAMETER`, `TEARDOWN`, `/feedback` ve `/audioMode` temel kontrol cevapları
- İngilizce ve Türkçe TV arayüzü; varsayılan dil İngilizce
- Ana ekran / Ayarlar / Tanılama ürün arayüzü

Mevcut transport listener'ları bilinçli olarak bir bring-up sınırıdır: sender'ın medya yollarını görüşüp açabildiğini doğrular. Sonraki katmanlar FairPlay stream-key işleme, medya şifre çözme, H.264 decode/rendering ve ses oynatmadır.

## Geliştirme durumu

AtrisCast henüz production-ready bir AirPlay receiver değildir. Uyumluluk gerçek Apple cihazlarından gelen handshake akışları üzerinden aşamalı olarak geliştirilmektedir. Tanılama ekranı, normal kullanıcı arayüzünü teknik ayrıntılarla doldurmadan en son başarıyla ulaşılan protokol aşamasını göstermeyi amaçlar.

## Gizlilik

AtrisCast tamamen yerel ağda çalışacak şekilde tasarlanır. AtrisHub login veya cloud bağlantısı gerektirmez ve casting trafiğini AtrisHub backend'ine göndermez.

## Proje

AtrisCast, AtrisHub ekosistemindeki bağımsız bir açık kaynak projedir.

- Web sitesi: `atrishub.com`
- Lisans: Apache License 2.0 (`LICENSE`)
- Üçüncü taraf bildirimleri: `THIRD_PARTY_NOTICES.md`

AirPlay, iPhone, iPad, Mac ve Apple TV Apple Inc. ticari markalarıdır. AtrisCast Apple Inc. ile bağlantılı, Apple tarafından onaylanmış veya sponsor edilmiş değildir.

## English

English README: [`README.md`](README.md)
