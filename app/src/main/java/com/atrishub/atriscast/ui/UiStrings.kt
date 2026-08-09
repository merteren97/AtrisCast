package com.atrishub.atriscast.ui

import com.atrishub.atriscast.receiver.ReceiverPhase
import com.atrishub.atriscast.receiver.ReceiverPreferences

internal enum class AppPage { HOME, SETTINGS, DIAGNOSTICS }
internal enum class ProtocolStage { DISCOVERY, NEGOTIATION, FAIRPLAY, TRANSPORT, STREAMING }

internal data class UiStrings(
    val home: String,
    val settings: String,
    val diagnostics: String,
    val homeTitle: String,
    val homeSubtitle: String,
    val settingsTitle: String,
    val settingsSubtitle: String,
    val diagnosticsTitle: String,
    val diagnosticsSubtitle: String,
    val ready: String,
    val connecting: String,
    val actionRequired: String,
    val receiverError: String,
    val starting: String,
    val offline: String,
    val readyForAirPlay: String,
    val deviceDetected: String,
    val tvReadyTitle: String,
    val connectingTitle: String,
    val permissionTitle: String,
    val errorTitle: String,
    val startingTitle: String,
    val stoppedTitle: String,
    val tvReadyBody: String,
    val connectingBody: String,
    val permissionBody: String,
    val errorBody: String,
    val startingBody: String,
    val stoppedBody: String,
    val currentStep: String,
    val localPrivate: String,
    val receiver: String,
    val network: String,
    val session: String,
    val airplayName: String,
    val waiting: String,
    val waitingForSender: String,
    val discoveryShort: String,
    val negotiateShort: String,
    val secureShort: String,
    val streamShort: String,
    val allowNetwork: String,
    val restartReceiver: String,
    val interfaceLanguage: String,
    val languageHelp: String,
    val englishHelp: String,
    val turkishHelp: String,
    val receiverBehavior: String,
    val behaviorHelp: String,
    val startOnBoot: String,
    val on: String,
    val off: String,
    val receiverName: String,
    val receiverNameHelp: String,
    val sessionProgress: String,
    val sessionProgressHelp: String,
    val discovery: String,
    val capabilities: String,
    val fairPlay: String,
    val transport: String,
    val media: String,
    val latestRequest: String,
    val receiverDetails: String,
    val localAddress: String,
    val endpoint: String,
    val profile: String,
    val sender: String,
    val none: String,
    val footer: String,
)

internal fun strings(language: String): UiStrings = if (language == ReceiverPreferences.LANGUAGE_TURKISH) {
    UiStrings(
        home = "Ana ekran",
        settings = "Ayarlar",
        diagnostics = "Tanılama",
        homeTitle = "Kablosuz ekran",
        homeSubtitle = "iPhone, iPad ve Mac için yerel AirPlay alıcısı",
        settingsTitle = "Ayarlar",
        settingsSubtitle = "AtrisCast'in bu TV'deki davranışını yönetin.",
        diagnosticsTitle = "Bağlantı tanılama",
        diagnosticsSubtitle = "AirPlay görüşmesinin hangi aşamaya ulaştığını canlı olarak görün.",
        ready = "HAZIR",
        connecting = "BAĞLANIYOR",
        actionRequired = "İŞLEM GEREKİYOR",
        receiverError = "ALICI HATASI",
        starting = "BAŞLATILIYOR",
        offline = "ÇEVRİMDIŞI",
        readyForAirPlay = "AIRPLAY İÇİN HAZIR",
        deviceDetected = "APPLE CİHAZI ALGILANDI",
        tvReadyTitle = "TV'niz paylaşım için hazır.",
        connectingTitle = "Cihazınız güvenli oturum kuruyor.",
        permissionTitle = "Yerel ağ erişimi gerekli.",
        errorTitle = "AtrisCast kontrol edilmeli.",
        startingTitle = "Alıcı hazırlanıyor…",
        stoppedTitle = "Alıcı durduruldu.",
        tvReadyBody = "iPhone, iPad veya Mac'inizde Ekran Yansıtma'yı açın ve AtrisCast'i seçin. Hesap ya da bulut bağlantısı gerekmez.",
        connectingBody = "AtrisCast cihazınızla AirPlay oturumunu görüşüyor. İlerleme aşağıda canlı olarak güncellenir.",
        permissionBody = "Android'in bu TV'yi yerel ağda yayınlaması ve bağlantı kabul etmesi için yerel ağ iznine izin verin.",
        errorBody = "Yerel alıcı başlatılamadı. Ağ bağlantısını kontrol edip alıcıyı yeniden başlatın.",
        startingBody = "AirPlay uç noktası ve yerel keşif servisleri hazırlanıyor.",
        stoppedBody = "Bu TV'yi Ekran Yansıtma listesinde göstermek için alıcıyı yeniden başlatın.",
        currentStep = "Şu an",
        localPrivate = "Yerel • Özel • Hesapsız",
        receiver = "Alıcı",
        network = "Ağ",
        session = "Oturum",
        airplayName = "AirPlay listesinde görünen ad",
        waiting = "Bekleniyor…",
        waitingForSender = "Apple cihazı bekleniyor",
        discoveryShort = "Keşif",
        negotiateShort = "Görüşme",
        secureShort = "Güvenli oturum",
        streamShort = "Yayın",
        allowNetwork = "Yerel ağa izin ver",
        restartReceiver = "Alıcıyı yeniden başlat",
        interfaceLanguage = "Arayüz dili",
        languageHelp = "TV arayüzünde kullanılacak dili seçin. Varsayılan dil İngilizce'dir.",
        englishHelp = "Varsayılan arayüz dili",
        turkishHelp = "Türkçe TV arayüzü",
        receiverBehavior = "Alıcı davranışı",
        behaviorHelp = "TV açılışı ve yerel receiver servisini yönetin.",
        startOnBoot = "TV açıldığında otomatik başlat",
        on = "Açık",
        off = "Kapalı",
        receiverName = "AirPlay adı",
        receiverNameHelp = "Apple cihazlarındaki Ekran Yansıtma listesinde bu ad görünür.",
        sessionProgress = "AirPlay oturumu",
        sessionProgressHelp = "Gerçek cihaz görüşmesinin ulaştığı son protokol aşaması.",
        discovery = "Yerel ağ keşfi",
        capabilities = "Yetenek bilgisi",
        fairPlay = "FairPlay oturumu",
        transport = "Medya taşıma kurulumu",
        media = "Ekran ve ses akışı",
        latestRequest = "Son protokol isteği",
        receiverDetails = "Alıcı ayrıntıları",
        localAddress = "Yerel adres",
        endpoint = "AirPlay uç noktası",
        profile = "Uyumluluk profili",
        sender = "Gönderici",
        none = "Yok",
        footer = "Tamamen yerel çalışır • AtrisHub hesabı gerekmez • atrishub.com",
    )
} else {
    UiStrings(
        home = "Home",
        settings = "Settings",
        diagnostics = "Diagnostics",
        homeTitle = "Wireless display",
        homeSubtitle = "Local AirPlay receiver for iPhone, iPad and Mac",
        settingsTitle = "Settings",
        settingsSubtitle = "Manage how AtrisCast behaves on this TV.",
        diagnosticsTitle = "Connection diagnostics",
        diagnosticsSubtitle = "See how far the live AirPlay negotiation has progressed.",
        ready = "READY",
        connecting = "CONNECTING",
        actionRequired = "ACTION REQUIRED",
        receiverError = "RECEIVER ERROR",
        starting = "STARTING",
        offline = "OFFLINE",
        readyForAirPlay = "READY FOR AIRPLAY",
        deviceDetected = "APPLE DEVICE DETECTED",
        tvReadyTitle = "Your TV is ready to share.",
        connectingTitle = "Your device is establishing a secure session.",
        permissionTitle = "Local network access is required.",
        errorTitle = "AtrisCast needs attention.",
        startingTitle = "Preparing the receiver…",
        stoppedTitle = "Receiver is stopped.",
        tvReadyBody = "Open Screen Mirroring on your iPhone, iPad or Mac and choose AtrisCast. No account or cloud connection is required.",
        connectingBody = "AtrisCast is negotiating the AirPlay session with your device. Progress updates live below.",
        permissionBody = "Allow local network access so Android can advertise this TV and accept an AirPlay connection.",
        errorBody = "The local receiver could not start. Check the network and restart the receiver.",
        startingBody = "Preparing the AirPlay endpoint and local discovery services.",
        stoppedBody = "Restart the receiver to make this TV visible in Screen Mirroring.",
        currentStep = "Current",
        localPrivate = "Local • Private • Account-free",
        receiver = "Receiver",
        network = "Network",
        session = "Session",
        airplayName = "Name shown in the AirPlay list",
        waiting = "Waiting…",
        waitingForSender = "Waiting for an Apple device",
        discoveryShort = "Discover",
        negotiateShort = "Negotiate",
        secureShort = "Secure session",
        streamShort = "Stream",
        allowNetwork = "Allow local network",
        restartReceiver = "Restart receiver",
        interfaceLanguage = "Interface language",
        languageHelp = "Choose the language used by the TV interface. English remains the default.",
        englishHelp = "Default interface language",
        turkishHelp = "Turkish TV interface",
        receiverBehavior = "Receiver behavior",
        behaviorHelp = "Manage TV startup behavior and the local receiver service.",
        startOnBoot = "Start automatically when the TV boots",
        on = "On",
        off = "Off",
        receiverName = "AirPlay name",
        receiverNameHelp = "This name appears in Screen Mirroring on Apple devices.",
        sessionProgress = "AirPlay session",
        sessionProgressHelp = "The latest stage reached by the real-device negotiation.",
        discovery = "Local discovery",
        capabilities = "Capability exchange",
        fairPlay = "FairPlay session",
        transport = "Media transport setup",
        media = "Screen and audio stream",
        latestRequest = "Latest protocol request",
        receiverDetails = "Receiver details",
        localAddress = "Local address",
        endpoint = "AirPlay endpoint",
        profile = "Compatibility profile",
        sender = "Sender",
        none = "None",
        footer = "Runs fully local • No AtrisHub account required • atrishub.com",
    )
}

internal fun protocolStage(lastRequest: String?): ProtocolStage {
    val request = lastRequest.orEmpty().uppercase()
    return when {
        request.startsWith("RECORD") -> ProtocolStage.STREAMING
        request.startsWith("SETUP") || request.startsWith("ANNOUNCE") -> ProtocolStage.TRANSPORT
        request.contains("/FP-SETUP") -> ProtocolStage.FAIRPLAY
        request.contains("/INFO") || request.startsWith("OPTIONS") -> ProtocolStage.NEGOTIATION
        else -> ProtocolStage.DISCOVERY
    }
}

internal fun stageTitle(stage: ProtocolStage, text: UiStrings): String = when (stage) {
    ProtocolStage.DISCOVERY -> text.discovery
    ProtocolStage.NEGOTIATION -> text.capabilities
    ProtocolStage.FAIRPLAY -> text.fairPlay
    ProtocolStage.TRANSPORT -> text.transport
    ProtocolStage.STREAMING -> text.media
}

internal fun statusLabel(phase: ReceiverPhase, text: UiStrings): String = when (phase) {
    ReceiverPhase.ADVERTISING -> text.ready
    ReceiverPhase.CLIENT_CONNECTED -> text.connecting
    ReceiverPhase.PERMISSION_REQUIRED -> text.actionRequired
    ReceiverPhase.ERROR -> text.receiverError
    ReceiverPhase.STARTING -> text.starting
    ReceiverPhase.STOPPED -> text.offline
}

internal fun heroEyebrow(phase: ReceiverPhase, text: UiStrings): String = when (phase) {
    ReceiverPhase.ADVERTISING -> text.readyForAirPlay
    ReceiverPhase.CLIENT_CONNECTED -> text.deviceDetected
    ReceiverPhase.PERMISSION_REQUIRED -> text.actionRequired
    ReceiverPhase.ERROR -> text.receiverError
    ReceiverPhase.STARTING -> text.starting
    ReceiverPhase.STOPPED -> text.offline
}

internal fun heroTitle(phase: ReceiverPhase, text: UiStrings): String = when (phase) {
    ReceiverPhase.ADVERTISING -> text.tvReadyTitle
    ReceiverPhase.CLIENT_CONNECTED -> text.connectingTitle
    ReceiverPhase.PERMISSION_REQUIRED -> text.permissionTitle
    ReceiverPhase.ERROR -> text.errorTitle
    ReceiverPhase.STARTING -> text.startingTitle
    ReceiverPhase.STOPPED -> text.stoppedTitle
}

internal fun heroBody(phase: ReceiverPhase, text: UiStrings): String = when (phase) {
    ReceiverPhase.ADVERTISING -> text.tvReadyBody
    ReceiverPhase.CLIENT_CONNECTED -> text.connectingBody
    ReceiverPhase.PERMISSION_REQUIRED -> text.permissionBody
    ReceiverPhase.ERROR -> text.errorBody
    ReceiverPhase.STARTING -> text.startingBody
    ReceiverPhase.STOPPED -> text.stoppedBody
}
