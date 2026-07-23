package id.walt.certificate.x509.truststore

import id.walt.certificate.x509.MockX509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals


class CompositeTrustStoreTest {

    @Test
    fun shouldBeDistinctBySubjectDnAndSerialNumber() {
        val certificate = MockX509Certificate("dn=a")
        val trustStoreA = InMemoryTrustStore(listOf(certificate))
        val trustStoreB = InMemoryTrustStore(listOf(certificate))
        val trustStore = CompositeTrustStore(listOf(trustStoreA, trustStoreB))
        val certificates = trustStore.findCertificateBySubjectDn(certificate.data.subjectDn)
        assertEquals(1, certificates.size)
    }

    @Test
    fun shouldFindCertificateBySubjectDnInChildTrustStores() {
        val certificate = MockX509Certificate("dn=a")
        val leafTrustStore = InMemoryTrustStore(listOf(certificate))
        val trustStoreMiddle = CompositeTrustStore(
            listOf(
                InMemoryTrustStore(),
                leafTrustStore,
                InMemoryTrustStore()
            )
        )
        val trustStore = CompositeTrustStore(
            listOf(
                InMemoryTrustStore(),
                trustStoreMiddle,
                InMemoryTrustStore()
            )
        )
        assertEquals(1, trustStore.findCertificateBySubjectDn("dn=a").size)
    }
}