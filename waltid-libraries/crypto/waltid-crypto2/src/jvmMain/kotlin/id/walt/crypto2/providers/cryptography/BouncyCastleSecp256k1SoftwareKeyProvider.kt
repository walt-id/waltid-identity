package id.walt.crypto2.providers.cryptography

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.jdk.JDK
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.providers.SoftwareKeyProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** JVM-only ES256K software keys backed by the maintained Bouncy Castle JCA provider. */
class BouncyCastleSecp256k1SoftwareKeyProvider(
    id: ProviderId = ProviderId("bouncycastle-secp256k1"),
) : SoftwareKeyProvider by CryptographySoftwareKeyProvider(
    provider = CryptographyProvider.JDK(BouncyCastleProvider()),
    id = id,
    profile = CryptographyCapabilityProfile.Secp256k1Signatures,
)
