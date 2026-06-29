package id.walt.x509

import id.walt.crypto.keys.Key

actual suspend fun platformBuildGenericX509Certificate(
    profileData: GenericX509CertificateProfileData,
    subjectPublicKey: Key,
    signingKey: Key,
): GenericX509CertificateBundle {
    // TODO(iOS): Implement generic X.509 certificate creation.
    TODO("Generic X.509 certificate creation is not implemented for iOS")
}
