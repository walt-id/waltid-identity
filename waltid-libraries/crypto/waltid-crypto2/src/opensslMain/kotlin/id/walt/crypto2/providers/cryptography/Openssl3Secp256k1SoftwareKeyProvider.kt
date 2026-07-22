package id.walt.crypto2.providers.cryptography

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.openssl3.Openssl3
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.providers.SoftwareKeyProvider

/** Linux and Windows native ES256K software keys backed by cryptography-kotlin OpenSSL 3. */
class Openssl3Secp256k1SoftwareKeyProvider(
    id: ProviderId = ProviderId("openssl3-secp256k1"),
) : SoftwareKeyProvider by CryptographySoftwareKeyProvider(
    provider = CryptographyProvider.Openssl3,
    id = id,
    profile = CryptographyCapabilityProfile.Secp256k1Signatures,
)
