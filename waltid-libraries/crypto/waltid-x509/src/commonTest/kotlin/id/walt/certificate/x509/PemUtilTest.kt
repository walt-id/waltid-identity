package id.walt.certificate.x509

import id.walt.certificate.TestData
import kotlin.test.Test
import kotlin.test.assertEquals

class PemUtilTest {

    @Test
    fun shouldNormalizeCertificatePem() {

        val expected = """
            -----BEGIN CERTIFICATE-----
            MIICETCCAbegAwIBAgIUMJAkGLbeyDnDaACHF2MwwUs/j1kwCgYIKoZIzj0EAwIw
            JDEVMBMGA1UEAwwMV2FsdCBJRCBSb290MQswCQYDVQQGEwJBVDAeFw0yNjA4MTAx
            MjUyNDdaFw0yNzExMTAxMjUyNDdaMCYxFzAVBgNVBAMMDldhbHQgSUQgbURMIERT
            MQswCQYDVQQGEwJBVDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABBtESDQYhfqE
            FA93eQxp3oELl3pyNvSQ2jBqDL4qCkQTed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT
            2JPUQK4lN4GjgcQwgcEwHQYDVR0OBBYEFLm7A+B7z8CQmFznE976TVpzBwXaMA4G
            A1UdDwEB/wQEAwIHgDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCoGA1UdEgQjMCGB
            Dm9mZmljZUB3YWx0Lmlkhg9odHRwczovL3dhbHQuaWQwLAYDVR0fBCUwIzAhoB+g
            HYYbaHR0cHM6Ly9jcmwud2FsdC5pZC9jcmwuZGVyMB8GA1UdIwQYMBaAFLm7A+B7
            z8CQmFznE976TVpzBwXaMAoGCCqGSM49BAMCA0gAMEUCIQD44E8Mukk3WwFeHbB6
            RZZPy85lVEyNqFZs6aNLq2kq4QIgXrURrzy1iLEYmsnna6YYhRrvGaYEjk1GqCn2
            w+skfmw=
            -----END CERTIFICATE-----
        """.trimIndent()

        val normalized = PemUtil.normalizePem(TestData.STRANGE_PEM)
        assertEquals(expected, normalized)
    }
}