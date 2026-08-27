@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.MontgomeryCurve
import id.walt.mdoc.objects.engagement.DeviceEngagementCapabilities

/** Versioned interoperability profiles whose rules are modeled by the proximity capability registry. */
enum class MdocProximityProfile(val id: String) {
    ISO_18013_5_2021("iso-18013-5:2021"),
    ISO_18013_5_ED2_DIS_2026("iso-18013-5:ed2-dis-2026"),
    EUDI_ARF_3_FCAF_2026_08("eudi-arf:3.0+fcaf:2026-08"),
}

enum class MdocProtocolFeature {
    NEGOTIATED_HANDOVER_SESSION_ESTABLISHMENT,
    READER_AUTH_ALL,
    EXTENDED_REQUESTS,
}

enum class MdocSessionCurve(val agreementAlgorithm: KeyAgreementAlgorithm) {
    P256(KeyAgreementAlgorithm.Ecdh),
    P384(KeyAgreementAlgorithm.Ecdh),
    P521(KeyAgreementAlgorithm.Ecdh),
    BRAINPOOL_P256R1(KeyAgreementAlgorithm.Ecdh),
    BRAINPOOL_P320R1(KeyAgreementAlgorithm.Ecdh),
    BRAINPOOL_P384R1(KeyAgreementAlgorithm.Ecdh),
    BRAINPOOL_P512R1(KeyAgreementAlgorithm.Ecdh),
    X25519(KeyAgreementAlgorithm.Xdh),
    X448(KeyAgreementAlgorithm.Xdh),
}

/** The four independent dimensions required before a feature or curve can be selected. */
data class MdocCapabilityState(
    val implemented: Boolean,
    val profilePermitted: Boolean,
    val runtimeAvailable: Boolean,
    val sessionSelected: Boolean,
) {
    init {
        require(!sessionSelected || implemented && profilePermitted && runtimeAvailable) {
            "A selected capability must be implemented, profile-permitted, and runtime-available"
        }
    }

    val available: Boolean get() = implemented && profilePermitted && runtimeAvailable
}

/** Immutable, provider-aware feature and curve decisions for one session. */
class MdocSessionCapabilities private constructor(
    val profile: MdocProximityProfile,
    featureStates: Map<MdocProtocolFeature, MdocCapabilityState>,
    curveStates: Map<MdocSessionCurve, MdocCapabilityState>,
) {
    val features: Map<MdocProtocolFeature, MdocCapabilityState> = featureStates.toMap()
    val curves: Map<MdocSessionCurve, MdocCapabilityState> = curveStates.toMap()

    val selectedCurve: MdocSessionCurve = curves.entries.single { it.value.sessionSelected }.key

    fun selected(feature: MdocProtocolFeature): Boolean = features.getValue(feature).sessionSelected

    internal fun toDeviceEngagementCapabilities(): DeviceEngagementCapabilities? =
        if (profile == MdocProximityProfile.ISO_18013_5_2021) null
        else DeviceEngagementCapabilities(
            handoverSessionEstablishment = selected(MdocProtocolFeature.NEGOTIATED_HANDOVER_SESSION_ESTABLISHMENT),
            readerAuthAll = selected(MdocProtocolFeature.READER_AUTH_ALL),
            extendedRequests = selected(MdocProtocolFeature.EXTENDED_REQUESTS),
        )

    companion object {
        private val implementedFeatures = MdocProtocolFeature.entries.toSet()
        private val implementedCurves = setOf(
            MdocSessionCurve.P256,
            MdocSessionCurve.P384,
            MdocSessionCurve.P521,
            MdocSessionCurve.X25519,
            MdocSessionCurve.X448,
        )
        private val nistCurves = setOf(MdocSessionCurve.P256, MdocSessionCurve.P384, MdocSessionCurve.P521)
        private val brainpoolCurves = setOf(
            MdocSessionCurve.BRAINPOOL_P256R1,
            MdocSessionCurve.BRAINPOOL_P320R1,
            MdocSessionCurve.BRAINPOOL_P384R1,
            MdocSessionCurve.BRAINPOOL_P512R1,
        )

        fun forSession(
            profile: MdocProximityProfile,
            key: Key,
            selectedFeatures: Set<MdocProtocolFeature>,
        ): MdocSessionCapabilities {
            val curve = key.spec.toMdocSessionCurve()
            val permittedFeatures = when (profile) {
                MdocProximityProfile.ISO_18013_5_2021 -> emptySet()
                MdocProximityProfile.ISO_18013_5_ED2_DIS_2026,
                MdocProximityProfile.EUDI_ARF_3_FCAF_2026_08 -> MdocProtocolFeature.entries.toSet()
            }
            val permittedCurves = when (profile) {
                MdocProximityProfile.ISO_18013_5_2021 -> nistCurves
                MdocProximityProfile.ISO_18013_5_ED2_DIS_2026 ->
                    nistCurves + brainpoolCurves + setOf(MdocSessionCurve.X25519, MdocSessionCurve.X448)
                MdocProximityProfile.EUDI_ARF_3_FCAF_2026_08 ->
                    nistCurves + brainpoolCurves - MdocSessionCurve.BRAINPOOL_P320R1
            }
            val featureStates = MdocProtocolFeature.entries.associateWith { feature ->
                MdocCapabilityState(
                    implemented = feature in implementedFeatures,
                    profilePermitted = feature in permittedFeatures,
                    runtimeAvailable = feature in implementedFeatures,
                    sessionSelected = feature in selectedFeatures,
                )
            }
            val curveStates = MdocSessionCurve.entries.associateWith { candidate ->
                val implemented = candidate in implementedCurves
                val runtimeAvailable = candidate == curve && key.capabilities.supportsKeyAgreementAlgorithm(
                    candidate.agreementAlgorithm,
                )
                MdocCapabilityState(
                    implemented = implemented,
                    profilePermitted = candidate in permittedCurves,
                    runtimeAvailable = runtimeAvailable,
                    sessionSelected = candidate == curve,
                )
            }
            return MdocSessionCapabilities(profile, featureStates, curveStates)
        }
    }
}

internal fun KeySpec.toMdocSessionCurve(): MdocSessionCurve = when (this) {
    KeySpec.Ec(EcCurve.P256) -> MdocSessionCurve.P256
    KeySpec.Ec(EcCurve.P384) -> MdocSessionCurve.P384
    KeySpec.Ec(EcCurve.P521) -> MdocSessionCurve.P521
    KeySpec.Ec(EcCurve.BRAINPOOL_P256R1) -> MdocSessionCurve.BRAINPOOL_P256R1
    KeySpec.Ec(EcCurve.BRAINPOOL_P384R1) -> MdocSessionCurve.BRAINPOOL_P384R1
    KeySpec.Ec(EcCurve.BRAINPOOL_P512R1) -> MdocSessionCurve.BRAINPOOL_P512R1
    KeySpec.Montgomery(MontgomeryCurve.X25519) -> MdocSessionCurve.X25519
    KeySpec.Montgomery(MontgomeryCurve.X448) -> MdocSessionCurve.X448
    else -> throw IllegalArgumentException("Unsupported mdoc session key specification: $this")
}
