package id.walt.wallet2.mobile.proximity

/**
 * Versioned mdoc interoperability boundary selected for one proximity session.
 *
 * @property id Stable identifier suitable for configuration and diagnostics.
 */
public enum class ProximityProfile(public val id: String) {
    /** ISO/IEC 18013-5:2021 compatibility boundary. */
    Iso1801352021("iso-18013-5:2021"),

    /** ISO/IEC 18013-5 edition-2 DIS implementation boundary. */
    Iso180135Edition2Dis2026("iso-18013-5:ed2-dis-2026"),

    /** EUDI ARF 3.0 plus the pinned August 2026 FCAF restrictions. */
    EudiArf3Fcaf202608("eudi-arf:3.0+fcaf:2026-08"),
}

/** BLE roles a holder may prepare for one in-person presentation. */
public enum class ProximityBleRoles {
    CentralClient,
    PeripheralServer,
    Dual,
}

/** Bearer selection policy kept separate from the app-visible session state. */
public enum class ProximityBleBearerPolicy {
    GattOnly,
    PreferL2cap,
}

/**
 * Complete BLE bearer configuration; it cannot exist unless BLE retrieval is selected.
 *
 * @property roles Holder GATT roles prepared for the session.
 * @property bearerPolicy GATT/L2CAP selection policy applied within the selected roles.
 */
public data class ProximityBleConfiguration(
    public val roles: ProximityBleRoles = ProximityBleRoles.Dual,
    public val bearerPolicy: ProximityBleBearerPolicy =
        ProximityBleBearerPolicy.PreferL2cap,
)

/**
 * Complete conventional NFC retrieval length contract.
 *
 * @property maximumCommandDataLength Maximum command-data bytes accepted by the holder.
 * @property maximumResponseDataLength Maximum response-data bytes returned by the holder.
 */
public data class ProximityNfcRetrievalConfiguration(
    public val maximumCommandDataLength: Int = 65_535,
    public val maximumResponseDataLength: Int = 65_536,
) {
    init {
        require(maximumCommandDataLength in 255..65_535)
        require(maximumResponseDataLength in 256..65_536)
    }
}

/**
 * Nonempty conventional retrieval plan used by QR or NFC handover.
 * @property bluetoothLowEnergy Optional BLE role and bearer policy.
 * @property nfc Optional conventional NFC command/response contract.
 * @property wifiAware Whether to offer Wi-Fi Aware with mandatory NCS-SK-128 security.
 */
public data class ProximityRetrievalOptions(
    public val bluetoothLowEnergy: ProximityBleConfiguration? = ProximityBleConfiguration(),
    public val nfc: ProximityNfcRetrievalConfiguration? = null,
    public val wifiAware: Boolean = false,
) {
    init { require(bluetoothLowEnergy != null || nfc != null || wifiAware) { "A retrieval plan must contain a bearer" } }
}

/** Conventional NFC Forum handover selection; provisional NFCv2 has its own session variant. */
public enum class ProximityNfcHandover {
    /** Holder-selected retrieval methods. */
    Static,
    /** Reader-selected retrieval method. */
    Negotiated,
}

/** Owns engagement and compatible retrieval together for one single-use session. */
public sealed interface ProximitySessionConfiguration {
    /**
     * QR engagement with a nonempty conventional retrieval plan.
     * @property retrieval Bearers advertised by the QR engagement.
     */
    public data class Qr(
        public val retrieval: ProximityRetrievalOptions =
            ProximityRetrievalOptions(),
    ) : ProximitySessionConfiguration

    /**
     * Conventional NFC handover with an optional, separately selected QR fallback.
     * @property handover NFC Forum handover mode.
     * @property retrieval Bearers offered through NFC handover.
     * @property qrFallback Nonempty QR plan. Shared bearers use the same BLE policy and conventional NFC length limits.
     */
    public data class ConventionalNfc(
        public val handover: ProximityNfcHandover,
        public val retrieval: ProximityRetrievalOptions,
        public val qrFallback: ProximityRetrievalOptions? = null,
    ) : ProximitySessionConfiguration {
        init {
            requireSharedBlePolicy(retrieval.bluetoothLowEnergy, qrFallback?.bluetoothLowEnergy)
            require(retrieval.nfc == null || qrFallback?.nfc == null || retrieval.nfc == qrFallback.nfc) {
                "QR and NFC handover must use the same conventional NFC retrieval length limits"
            }
        }
    }

    /**
     * Provisional NFCv2 engagement and its mandatory same-channel retrieval.
     * @property maximumCommandDataLength Maximum data accepted by the NFCv2 application.
     * @property bluetoothLowEnergy Optional NFCv2 hybrid BLE bearer.
     * @property qrFallback Nonempty conventional plan advertised by QR, when selected.
     * @property wifiAware Whether to offer a hybrid Wi-Fi Aware bearer with mandatory NCS-SK-128 security.
     */
    public data class ProvisionalNfcV2(
        public val maximumCommandDataLength: Int = 65_536,
        public val bluetoothLowEnergy: ProximityBleConfiguration? = null,
        public val qrFallback: ProximityRetrievalOptions? = null,
        public val wifiAware: Boolean = false,
    ) : ProximitySessionConfiguration {
        init {
            require(maximumCommandDataLength in 1..65_536)
            requireSharedBlePolicy(bluetoothLowEnergy, qrFallback?.bluetoothLowEnergy)
        }
    }
}

/** Holder authentication frozen for a reviewed document response. */
public enum class ProximityDeviceAuthenticationMethod {
    Signature,
    Mac,
}

/** Explicit allowlist and preference applied before an immutable review is constructed. */
public enum class ProximityDeviceAuthenticationPolicy(
    internal val preferenceOrder: List<ProximityDeviceAuthenticationMethod>,
) {
    /** Require device signature; credentials whose keys cannot sign are unavailable. */
    SignatureOnly(listOf(ProximityDeviceAuthenticationMethod.Signature)),

    /** Require device MAC; credentials whose keys cannot agree a MAC key are unavailable. */
    MacOnly(listOf(ProximityDeviceAuthenticationMethod.Mac)),

    /** Prefer signature and fall back to MAC only before constructing the immutable review. */
    PreferSignature(
        listOf(
            ProximityDeviceAuthenticationMethod.Signature,
            ProximityDeviceAuthenticationMethod.Mac,
        ),
    ),

    /** Prefer MAC and fall back to signature only before constructing the immutable review. */
    PreferMac(
        listOf(
            ProximityDeviceAuthenticationMethod.Mac,
            ProximityDeviceAuthenticationMethod.Signature,
        ),
    ),
}

/** Policy applied after reader authentication and trust facts have been evaluated. */
public enum class ProximityReaderPolicy {
    /** Anonymous or cryptographically valid but untrusted readers may reach explicit holder consent. */
    AllowAnonymousOrUntrusted,

    /** A cryptographically valid and trusted reader is required before any disclosure preview is shown. */
    RequireTrusted,
}

/**
 * Immutable configuration for one single-use proximity session.
 *
 * @property profile Protocol and application-profile boundary to enforce.
 * @property session Engagement and compatible retrieval plans owned by this session.
 * @property readerPolicy Trust threshold applied before disclosure review.
 * @property deviceAuthenticationPolicy Allowed and preferred holder-authentication methods.
 * @property readerTrustEvaluator Application-owned reader trust boundary.
 * @property credentialStatusEvaluator Application-owned credential status boundary.
 * @property applicationProfiles Ordered application-profile registry for request extensions.
 * @property maximumMessageBytes Maximum accepted encoded proximity message size.
 */
public data class ProximityConfiguration(
    public val profile: ProximityProfile =
        ProximityProfile.Iso180135Edition2Dis2026,
    public val session: ProximitySessionConfiguration = ProximitySessionConfiguration.Qr(),
    public val readerPolicy: ProximityReaderPolicy =
        ProximityReaderPolicy.AllowAnonymousOrUntrusted,
    public val deviceAuthenticationPolicy: ProximityDeviceAuthenticationPolicy =
        ProximityDeviceAuthenticationPolicy.SignatureOnly,
    public val readerTrustEvaluator: ProximityReaderTrustEvaluator =
        UnconfiguredProximityReaderTrustEvaluator,
    public val credentialStatusEvaluator: ProximityCredentialStatusEvaluator =
        UnconfiguredProximityCredentialStatusEvaluator,
    public val applicationProfiles: ProximityApplicationProfileRegistry =
        ProximityApplicationProfileRegistry.Empty,
    public val maximumMessageBytes: Int = 1_048_576,
    /** Explicit holder-approval behavior, independent of engagement and retrieval methods. */
    public val approval: ProximityApproval = ProximityApproval.AskEachTime,
) {
    init {
        require(maximumMessageBytes in 1..16_777_216) {
            "Maximum proximity message size must be between 1 byte and 16 MiB"
        }
        require(
            profile != ProximityProfile.EudiArf3Fcaf202608 ||
                readerPolicy == ProximityReaderPolicy.RequireTrusted
        ) { "The selected EUDI profile requires an authenticated and trusted reader" }
        require(
            profile != ProximityProfile.EudiArf3Fcaf202608 ||
                deviceAuthenticationPolicy == ProximityDeviceAuthenticationPolicy.SignatureOnly
        ) { "The selected EUDI profile requires device-signature authentication" }
        require(profile != ProximityProfile.Iso1801352021 || session !is ProximitySessionConfiguration.ProvisionalNfcV2) {
            "NFC Engagement v2 is not part of the ISO/IEC 18013-5:2021 compatibility profile"
        }

    }
}
