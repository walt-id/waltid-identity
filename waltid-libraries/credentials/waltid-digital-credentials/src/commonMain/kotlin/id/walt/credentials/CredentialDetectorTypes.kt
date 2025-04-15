package id.walt.credentials

import kotlinx.serialization.Serializable

object CredentialDetectorTypes {

    /** Primary type of credential. For specific subtypes check interface [CredentialSubDataType]. */
    enum class CredentialPrimaryDataType {
        W3C,
        SDJWTVC,
        MDOCS
    }

    /** Specific subtype of a credential */
    sealed interface CredentialSubDataType

    /** Specific types of a W3C credential */
    enum class W3CSubType : CredentialSubDataType {
        W3C_1_1,
        W3C_2,
    }

    /** Specific types of a SD-JWT VC credential */
    enum class SDJWTVCSubType : CredentialSubDataType {
        sdjwtvcdm,
        sdjwtvc
    }

    /** Specific types of a Mdocs credential */
    enum class MdocsSubType : CredentialSubDataType {
        mdocs
    }

    // Todo: This was replaced with more specific containsDisclosures & providesDisclosures for now. However,
    // possibly there will be other flags too?
    enum class CredentialFlags {
        SD_JWT
    }

    /** Type of signature this credential is signed with ([SignaturePrimaryType.UNSIGNED] if it is not signed). */
    enum class SignaturePrimaryType {
        UNSIGNED,
        JWT,
        SDJWT,
        DATA_INTEGRITY_PROOF,
        COSE
    }

    /**
     * Just the types of the credential, as provided by the credential detector.
     * The Result with data is contained in [CredentialDetectionResult].
     */
    /*@Serializable
    data class CredentialDetectionResultTypes(
        val credentialPrimaryType: CredentialPrimaryDataType,
        val credentialSubType: CredentialSubDataType,
        val signaturePrimary: SignaturePrimaryType,

        *//** Does this credential contain a list of disclosures? *//*
        val containsDisclosures: Boolean = false,
        *//** Have disclosures been shared with this credential? *//*
        val providesDisclosures: Boolean = false
    )*/

    /**
     * Result of the [CredentialParser].
     */
    @Serializable
    data class CredentialDetectionResult(
        val credentialPrimaryType: CredentialPrimaryDataType,
        val credentialSubType: CredentialSubDataType,
        val signaturePrimary: SignaturePrimaryType,

        /** Does this credential contain a list of disclosables? */
        val containsDisclosables: Boolean = false,
        /** Have disclosures been shared with this credential? */
        val providesDisclosures: Boolean = false
    ) {

        init {
            when (signaturePrimary) {
                SignaturePrimaryType.SDJWT -> check(providesDisclosures) { "Credential has primary signature type SD-JWT, but provides no disclosures? -> for $this" }
                SignaturePrimaryType.JWT, SignaturePrimaryType.UNSIGNED -> check(!providesDisclosures) { "Credential has primary signature type $signaturePrimary, but provides disclosures? -> for $this" }

                else -> {}
            }

            if (!containsDisclosables && providesDisclosures) {
                throw IllegalArgumentException("Credential does not contain any selective disclosures; but disclosures were provided? -> for $this")
            }
        }
    }
}
