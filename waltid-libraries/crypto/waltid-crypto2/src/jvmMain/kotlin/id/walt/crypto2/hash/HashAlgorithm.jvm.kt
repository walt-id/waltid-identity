package id.walt.crypto2.hash

actual fun HashAlgorithm.createHasher() = JcaHasher(this) as Hasher

actual fun HashAlgorithm.createDigest() = JcaDigest(this) as Digest