package id.walt.x509

import id.walt.crypto.keys.Key

actual suspend fun platformBuildGenericX509Certificate(
    profileData: GenericX509CertificateProfileData,
    subjectPublicKey: Key,
    signingKey: Key,
): GenericX509CertificateBundle {
    TODO("Generic X.509 certificate creation is not implemented for JavaScript")
}
