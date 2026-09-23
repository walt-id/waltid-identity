package id.walt.certificate.x509

import id.walt.certificate.x509.validation.X509CertificateChainValidator
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import id.walt.certificate.x509.validation.validator.X509CertificateValidityValidator
import id.walt.x509.id.walt.certificate.x509.JavaX509CertificateSerialNumberGenerator
import id.walt.certificate.x509.bouncycastle.BouncyPkcs10CertificateSigningRequestParser
import id.walt.certificate.x509.bouncycastle.BouncyPkcs10CertificateSigningRequestSigner
import id.walt.certificate.x509.bouncycastle.BouncyX509CertificateParser
import id.walt.certificate.x509.bouncycastle.BouncyX509CertificateSigner
import id.walt.certificate.x509.validation.validator.X509CertificateBasicConstraintsValidator
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.x509.id.walt.certificate.x509.javasec.JavaDefaultTrustStore
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Provider
import java.security.Security
import kotlin.jvm.java

actual fun platformDefaultServices(): X509CertificateServices {
    initBouncyCastleProvider()
    val certificateParser = BouncyX509CertificateParser()
    val certificateSigner = BouncyX509CertificateSigner()
    return X509CertificateServices(
        cryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders()),
        csrParser = BouncyPkcs10CertificateSigningRequestParser(),
        csrSigner = BouncyPkcs10CertificateSigningRequestSigner(),
        certificateParser = certificateParser,
        signatureValidator = certificateSigner,
        serialNumberGenerator = JavaX509CertificateSerialNumberGenerator(),
        certificateSigner = certificateSigner,
        certificateChainValidator = X509CertificateChainValidator(
            listOf(
                X509CertificateValidityValidator(),
                X509CertificateBasicConstraintsValidator(),
                X509CertificateSignatureValidator(certificateSigner)
            ),
            JavaDefaultTrustStore(certificateParser)
        )
    )
}

var bouncyCastleProviderInitialized = false

fun initBouncyCastleProvider() {
    if (bouncyCastleProviderInitialized) return

    val provider: Provider? = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
    if (provider == null) {
        // Web3j will set up the provider lazily when it's first used.
        return
    }
    if (provider::class.equals(BouncyCastleProvider::class.java)) {
        // BC with same package name, shouldn't happen in real life.
        return
    }


    // Android registers its own BC provider. As it might be outdated and might not include
    // all needed ciphers, we substitute it with a known BC bundled in the app.
    // Android's BC has its package rewritten to "com.android.org.bouncycastle" and because
    // of that it's possible to have another BC implementation loaded in VM.
    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
    Security.insertProviderAt(BouncyCastleProvider(), 1)
    // Register Bouncy Castle Provider
    Security.addProvider(BouncyCastleProvider())
    bouncyCastleProviderInitialized = true
}
