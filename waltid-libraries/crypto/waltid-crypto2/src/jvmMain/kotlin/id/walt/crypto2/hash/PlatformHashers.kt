package id.walt.crypto2.hash

internal class JvmDefaultHasherProvider: HasherProvider {
    override fun create(algorithm: HashAlgorithm): Hash = JcaHash(algorithm)
}

internal actual fun getPlatformDefaultHasherProvider() = JvmDefaultHasherProvider() as HasherProvider

internal class JvmDefaultDigestProvider: DigestProvider {
    override fun create(algorithm: HashAlgorithm): Digest = JcaDigest(algorithm)
}

internal actual fun getPlatformDefaultDigestProvider() = JvmDefaultDigestProvider() as DigestProvider
