package id.walt.did.dids.document.models.verification.method

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Enumerated type representing verification method types that are registered in the [Decentralized Identifier Extensions](https://www.w3.org/TR/2024/NOTE-did-spec-registries-20240830/#verification-method-types) parameter registry.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
enum class VerificationMethodType {
    JsonWebKey2020,
    EcdsaSecp256k1VerificationKey2019,
    Ed25519VerificationKey2020, //it used to be 2018 for base58 encoding, but since that is deprecated in favor of multibase it is updated
    Bls12381G1Key2020,
    Bls12381G2Key2020,
    PgpVerificationKey2021,
    RsaVerificationKey2018,
    X25519KeyAgreementKey2019,
    EcdsaSecp256k1RecoveryMethod2020,
    VerifiableCondition2021;
}