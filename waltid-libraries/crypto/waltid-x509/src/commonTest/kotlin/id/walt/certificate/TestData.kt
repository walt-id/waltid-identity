package id.walt.certificate

object TestData {
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
        -----BEGIN RSA PRIVATE KEY-----
        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
        -----END RSA PRIVATE KEY-----""".trimIndent()

    val intermediateIssuerPrivateKey = """
        -----BEGIN EC PRIVATE KEY-----
        MHcCAQEEIHMSqUBLc5jykCXKC2ue/HvEmuUZ6nmF2/ME/nKogZx3oAoGCCqGSM49
        AwEHoUQDQgAED2LUa7lbsK75ysPikRkQQoOe1GcMHAEh5Y7/Jpg1Eb3vODz541LL
        1PUgq+vSYgcrUUytmIl5hT/WncJbAOl3kw==
        -----END EC PRIVATE KEY-----            
        """.trimIndent()

    val intermediateIssuerPublicKeyHex = """
        04:0f:62:d4:6b:b9:5b:b0:ae:f9:ca:c3:e2:91:19:
        10:42:83:9e:d4:67:0c:1c:01:21:e5:8e:ff:26:98:
        35:11:bd:ef:38:3c:f9:e3:52:cb:d4:f5:20:ab:eb:
        d2:62:07:2b:51:4c:ad:98:89:79:85:3f:d6:9d:c2:
        5b:00:e9:77:93
    """.replace("[\\s:]".toRegex(),"")

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