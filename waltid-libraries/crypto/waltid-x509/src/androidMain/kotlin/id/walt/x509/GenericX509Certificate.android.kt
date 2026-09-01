package id.walt.x509

actual suspend fun platformBuildGenericX509Certificate(
    profileData: GenericX509CertificateProfileData,
    subjectPublicKey: id.walt.crypto.keys.Key,
    signingKey: id.walt.crypto.keys.Key
): GenericX509CertificateBundle {
    TODO("Not yet implemented")
}