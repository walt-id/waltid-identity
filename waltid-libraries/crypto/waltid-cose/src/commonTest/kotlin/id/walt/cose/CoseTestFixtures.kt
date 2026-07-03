package id.walt.cose

internal object CoseTestFixtures {
    private val signatureSuffix = "cose-test-signature".encodeToByteArray()

    // Common COSE tests exercise structure/serialization; platform crypto has dedicated tests.
    val signer = CoseSigner { data -> data + signatureSuffix }

    val verifier = CoseVerifier { data, signature ->
        signature.contentEquals(data + signatureSuffix)
    }
}
