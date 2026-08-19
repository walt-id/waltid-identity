package id.walt.certificate

object TestData {
    val STRANGE_PEM = """
        -----BEGIN CERTIFICATE-----
          MIICETCCAbegAwIBAgIUMJAkGLbeyDnDaACHF2MwwUs/j1kwCgYIKoZIzj0EAwIwJDEVMBMGA1UEAwwMV2FsdCBJRCBSb290MQswCQYDVQQGEwJBVDAeFw0yNjA4MTAxMjUyNDdaFw0yNzExMTAxMjUyNDdaMCYxFzAVBgNVBAMMDldhbHQgSUQgbURMIERTMQswCQYDVQQGEwJBVDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABBtESDQYhfqEFA93eQxp3oELl3pyNvSQ2jBqDL4qCkQTed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4GjgcQwgcEwHQYDVR0OBBYEFLm7A+B7z8CQmFznE976TVpzBwXaMA4GA1UdDwEB/wQEAwIHgDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCoGA1UdEgQjMCGBDm9mZmljZUB3YWx0Lmlkhg9odHRwczovL3dhbHQuaWQwLAYDVR0fBCUwIzAhoB+gHYYbaHR0cHM6Ly9jcmwud2FsdC5pZC9jcmwuZGVyMB8GA1UdIwQYMBaAFLm7A+B7z8CQmFznE976TVpzBwXaMAoGCCqGSM49BAMCA0gAMEUCIQD44E8Mukk3WwFeHbB6RZZPy85lVEyNqFZs6aNLq2kq4QIgXrURrzy1iLEYmsnna6YYhRrvGaYEjk1GqCn2w+skfmw=
            -----END CERTIFICATE-----
    """.trimIndent()

    val GOOGLE_CERTIFICATE_PEM = """
        -----BEGIN CERTIFICATE-----
        MIIHszCCB1mgAwIBAgIQS/yZYC95Bm8SyoZxn+BZYDAKBggqhkjOPQQDAjA7MQsw
        CQYDVQQGEwJVUzEeMBwGA1UEChMVR29vZ2xlIFRydXN0IFNlcnZpY2VzMQwwCgYD
        VQQDEwNXRTIwHhcNMjYwNjE1MDgzOTA2WhcNMjYwOTA3MDgzOTA1WjAXMRUwEwYD
        VQQDDAwqLmdvb2dsZS5jb20wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQZPLpa
        GzC510PWtSDWCAgjMG7gAna4hKiqiJcB95MMm4b0YXrmuVrHoJOOW/4rOoS+Ho13
        rxlYI5DhY7kJgjjpo4IGYTCCBl0wDgYDVR0PAQH/BAQDAgeAMBMGA1UdJQQMMAoG
        CCsGAQUFBwMBMAwGA1UdEwEB/wQCMAAwHQYDVR0OBBYEFHgUdqi/fk4pXnKWpYDd
        wuEsoEavMB8GA1UdIwQYMBaAFHW+xHeuifZEN33PsWgfHRrr3DRZMFgGCCsGAQUF
        BwEBBEwwSjAhBggrBgEFBQcwAYYVaHR0cDovL28ucGtpLmdvb2cvd2UyMCUGCCsG
        AQUFBzAChhlodHRwOi8vaS5wa2kuZ29vZy93ZTIuY3J0MIIEOAYDVR0RBIIELzCC
        BCuCDCouZ29vZ2xlLmNvbYIWKi5hcHBlbmdpbmUuZ29vZ2xlLmNvbYIJKi5iZG4u
        ZGV2ghUqLm9yaWdpbi10ZXN0LmJkbi5kZXaCEiouY2xvdWQuZ29vZ2xlLmNvbYIY
        Ki5jcm93ZHNvdXJjZS5nb29nbGUuY29tghgqLmRhdGFjb21wdXRlLmdvb2dsZS5j
        b22CCyouZ29vZ2xlLmNhggsqLmdvb2dsZS5jbIIOKi5nb29nbGUuY28uaW6CDiou
        Z29vZ2xlLmNvLmpwgg4qLmdvb2dsZS5jby51a4IPKi5nb29nbGUuY29tLmFygg8q
        Lmdvb2dsZS5jb20uYXWCDyouZ29vZ2xlLmNvbS5icoIPKi5nb29nbGUuY29tLmNv
        gg8qLmdvb2dsZS5jb20ubXiCDyouZ29vZ2xlLmNvbS50coIPKi5nb29nbGUuY29t
        LnZuggsqLmdvb2dsZS5kZYILKi5nb29nbGUuZXOCCyouZ29vZ2xlLmZyggsqLmdv
        b2dsZS5odYILKi5nb29nbGUuaXSCCyouZ29vZ2xlLm5sggsqLmdvb2dsZS5wbIIL
        Ki5nb29nbGUucHSCGSouZ2VtaW5pLmNsb3VkLmdvb2dsZS5jb22CDSouZ3N0YXRp
        Yy5jb22CFCoubWV0cmljLmdzdGF0aWMuY29tggoqLmd2dDEuY29tghEqLmdjcGNk
        bi5ndnQxLmNvbYIKKi5ndnQyLmNvbYIOKi5nY3AuZ3Z0Mi5jb22CECoudXJsLmdv
        b2dsZS5jb22CFioueW91dHViZS1ub2Nvb2tpZS5jb22CCyoueXRpbWcuY29tggph
        aS5hbmRyb2lkggthbmRyb2lkLmNvbYINKi5hbmRyb2lkLmNvbYITKi5mbGFzaC5h
        bmRyb2lkLmNvbYIEZy5jb4IGKi5nLmNvggZnb28uZ2yCCnd3dy5nb28uZ2yCFGdv
        b2dsZS1hbmFseXRpY3MuY29tghYqLmdvb2dsZS1hbmFseXRpY3MuY29tggpnb29n
        bGUuY29tghJnb29nbGVjb21tZXJjZS5jb22CFCouZ29vZ2xlY29tbWVyY2UuY29t
        ggp1cmNoaW4uY29tggwqLnVyY2hpbi5jb22CCHlvdXR1LmJlggt5b3V0dWJlLmNv
        bYINKi55b3V0dWJlLmNvbYIRbXVzaWMueW91dHViZS5jb22CEyoubXVzaWMueW91
        dHViZS5jb22CFHlvdXR1YmVlZHVjYXRpb24uY29tghYqLnlvdXR1YmVlZHVjYXRp
        b24uY29tgg95b3V0dWJla2lkcy5jb22CESoueW91dHViZWtpZHMuY29tggV5dC5i
        ZYIHKi55dC5iZYIaYW5kcm9pZC5jbGllbnRzLmdvb2dsZS5jb22CFSouYWlzdHVk
        aW8uZ29vZ2xlLmNvbTATBgNVHSAEDDAKMAgGBmeBDAECATA2BgNVHR8ELzAtMCug
        KaAnhiVodHRwOi8vYy5wa2kuZ29vZy93ZTIveUs1blBodEhLUXMuY3JsMIIBAwYK
        KwYBBAHWeQIEAgSB9ASB8QDvAHYA1219ENGn9XfCx+lf1wC/+YLJM1pl4dCzAXMX
        wMjFaXcAAAGeyqYtrAAABAMARzBFAiEA38rjpcwGfrN37p3cSQSkIH9JfMRThylD
        8Xp+jaCf0uICIG0X5oAh3ivlmPLNjBA32vbIthEkGU466dvn96bnNAMxAHUAyKPE
        f8ezrbk1awE/anoSbeM6TkOlxkb5l605dZkdz5oAAAGeyqYtgAAABAMARjBEAiAR
        acMb65SscNtGurAhrH/cqoPn3TJwvRsXNfZVHPmj7AIgF3EtBVTv1DmTPe/qr42D
        IAmRaodpnmI47YLmMo5xcvswCgYIKoZIzj0EAwIDSAAwRQIgQTIfQp7FQv2Qqvy6
        yJjcCFZS9yUUKyIVKbKTRWk9Ib4CIQCyb20IOS7TVptYoTKutWYS4ulk1CLOtNXq
        AvegDlZ0yQ==
        -----END CERTIFICATE-----""".trimIndent()

    val V_TRUST_ROOT_CA_CERTIFICATE_PEM = """
        -----BEGIN CERTIFICATE-----
        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
        sBxXVsFy6K2ir40zSbofitzmdHxghm+Hl3s=
        -----END CERTIFICATE-----""".trimIndent()

    val caIssuerPrivateKey = """
        -----BEGIN PRIVATE KEY-----
        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
        rM4q71uirTzhbTu9DC/XCaqus8+HGC0=
        -----END PRIVATE KEY-----""".trimIndent()

    val intermediateIssuerPrivateKeyPem = """
        -----BEGIN PRIVATE KEY-----
        MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgpCx4+BY+9c+2CRpO
        b1r1KiBXU2WwyN85svFYSaH9O8WhRANCAATj3KZxtouhG7C7t3wrAkdDY9W/ppM0
        7WfhnOH8Uz7oL2AYyKf49GR6yQsrD4WsMeZ/rdpF+aOA7Di8/nYwlJyY
        -----END PRIVATE KEY-----
        """.trimIndent()

    val intermediateIssuerPublicKeyPem = """
        -----BEGIN PUBLIC KEY-----
        MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE49ymcbaLoRuwu7d8KwJHQ2PVv6aT
        NO1n4Zzh/FM+6C9gGMin+PRkeskLKw+FrDHmf63aRfmjgOw4vP52MJScmA==
        -----END PUBLIC KEY-----
        """.trimIndent()

    val intermediateIssuerKeyPem = intermediateIssuerPrivateKeyPem +
            "\n" +
            intermediateIssuerPublicKeyPem

    val intermediateIssuerPublicKeyValueHex = """
        04:e3:dc:a6:71:b6:8b:a1:1b:b0:bb:b7:7c:2b:02:
        47:43:63:d5:bf:a6:93:34:ed:67:e1:9c:e1:fc:53:
        3e:e8:2f:60:18:c8:a7:f8:f4:64:7a:c9:0b:2b:0f:
        85:ac:31:e6:7f:ad:da:45:f9:a3:80:ec:38:bc:fe:
        76:30:94:9c:98""".trimIndent().replace("[\\s:]".toRegex(), "")

    val intermediateIssuerPublicKeyIdHex = """
         a7:97:e8:20:1f:a9:32:29:98:9d:ae:73:2d:3a:a6:ec:ba:e5:81:09
    """.trimIndent().replace("[\\s:]".toRegex(), "")

    val csrPem = """
        -----BEGIN CERTIFICATE REQUEST-----
        MIIBVDCB+gIBADBWMQswCQYDVQQGEwJBVDEPMA0GA1UECAwGVmllbm5hMQ8wDQYD
        VQQHDAZWaWVubmExEDAOBgNVBAoMB1dhbHQuaWQxEzARBgNVBAMMCjovL3dhbHQu
        aWQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQPYtRruVuwrvnKw+KRGRBCg57U
        ZwwcASHljv8mmDURve84PPnjUsvU9SCr69JiBytRTK2YiXmFP9adwlsA6XeToEIw
        QAYJKoZIhvcNAQkOMTMwMTAvBgNVHREEKDAmgg06Ly93YWx0aWQuY29tgg86Ly93
        YWx0aWQuY2xvdWSHBMCoAWQwCgYIKoZIzj0EAwIDSQAwRgIhAO1DSVQyWDTmthCP
        m9KPWgOECYZt7ktHDpL3CdIcDCIaAiEA56yBVM2dKSj5jesIw7eCHCvioe29khhs
        rLF38kdqQqg=
        -----END CERTIFICATE REQUEST-----""".trimIndent()


    val csrWithCrlPem = """
        -----BEGIN CERTIFICATE REQUEST-----
        MIIB4jCCAYgCAQAwgYoxCzAJBgNVBAYTAkFUMRYwFAYDVQQIDA1Mb3dlciBBdXN0
        cmlhMRgwFgYDVQQHDA9PYmVyLUdyYWZlbmRvcmYxGDAWBgNVBAoMD015IE9yZ2Fu
        aXphdGlvbjEWMBQGA1UECwwNSVQgRGVwYXJ0bWVudDEXMBUGA1UEAwwOeW91cmRv
        bWFpbi5jb20wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQPYtRruVuwrvnKw+KR
        GRBCg57UZwwcASHljv8mmDURve84PPnjUsvU9SCr69JiBytRTK2YiXmFP9adwlsA
        6XeToIGaMIGXBgkqhkiG9w0BCQ4xgYkwgYYwCQYDVR0TBAIwADALBgNVHQ8EBAMC
        BaAwLAYDVR0RBCUwI4IOeW91cmRvbWFpbi5jb22CETovL3lvdXJkb21haW4uY29t
        MD4GA1UdHwQ3MDUwM6AtoSswKQYDVQQDDCJNeSBSZWxhdGl2ZSBDUkwgRGlzdHJp
        YnV0aW9uIFBvaW50gQIBfjAKBggqhkjOPQQDAgNIADBFAiEAkzC1hubU2JctG2ms
        o+1uuwhvGV/khbcOj6h6RW1Ny0UCIHeZIrK0BVkaIIHw6lIsA7ypEKeixXSKdvEc
        pwq6xEhp
        -----END CERTIFICATE REQUEST-----
""".trimIndent()
}