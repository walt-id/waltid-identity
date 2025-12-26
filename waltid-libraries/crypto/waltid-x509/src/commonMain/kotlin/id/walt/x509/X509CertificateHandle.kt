package id.walt.x509

internal interface X509CertificateHandle {

    fun getCertificateDer(): CertificateDer
}