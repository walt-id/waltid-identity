package id.walt.did.dids.document.models.verification.method

import kotlinx.serialization.Serializable

//as defined in https://www.w3.org/TR/2024/NOTE-did-spec-registries-20240830/#verification-method-types
@Serializable
enum class VerificationMethodType {
    JsonWebKey2020,
    EcdsaSecp256k1VerificationKey2019,
    Ed25519VerificationKey2018,
    Bls12381G1Key2020,
    Bls12381G2Key2020,
    PgpVerificationKey2021,
    RsaVerificationKey2018,
    X25519KeyAgreementKey2019,
    EcdsaSecp256k1RecoveryMethod2020,
    VerifiableCondition2021;
}