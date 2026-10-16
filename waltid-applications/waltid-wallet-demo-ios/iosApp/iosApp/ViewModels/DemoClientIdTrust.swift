import Foundation
import WalletSDK

/// Example verifier trust anchors for the native iOS demo.
///
/// OpenID4VP `x509_san_dns` and `x509_hash` clients fail closed unless the wallet pins at least one
/// X.509 trust anchor. `decentralized_identifier` clients still authenticate through DID resolution
/// and do not use this list.
///
/// Keep this list aligned with the Compose demo `DemoClientIdTrust`. Replace or append entries in
/// `x509TrustAnchorPems` with your own CAs (or pinned leaves) before shipping a production wallet.
enum DemoClientIdTrust {
    static let verifier2ExampleLeafPem = """
        -----BEGIN CERTIFICATE-----
        MIIB2DCCAX+gAwIBAgIUHM9IDlzSNPwZcKStPxhcDrQBxPEwCgYIKoZIzj0EAwIw
        MTEdMBsGA1UEAwwUdmVyaWZpZXIuZXhhbXBsZS5jb20xEDAOBgNVBAoMB3dhbHQu
        aWQwHhcNMjYwODEzMDAwMDAwWhcNMjgwODEzMDAwMDAwWjAxMR0wGwYDVQQDDBR2
        ZXJpZmllci5leGFtcGxlLmNvbTEQMA4GA1UECgwHd2FsdC5pZDBZMBMGByqGSM49
        AgEGCCqGSM49AwEHA0IABBv04AXNAZJjIqYkP+oJGpiJ95pqe4XE62bsqy05pJz9
        VkRMZdXYXSMff5AJLrnHiN0x5MV6u/8vrAcytGUe4z6jdTBzMAwGA1UdEwEB/wQC
        MAAwDgYDVR0PAQH/BAQDAgeAMBMGA1UdJQQMMAoGCCsGAQUFBwMCMB8GA1UdEQQY
        MBaCFHZlcmlmaWVyLmV4YW1wbGUuY29tMB0GA1UdDgQWBBSCJZ2bjB1VuLM8lvN8
        e/M4MYmLvzAKBggqhkjOPQQDAgNHADBEAiAI8wYnWdTWeqNLKMVw8UU4xPPnZkR4
        5NhD/iUNKexRdgIgCdTIULB9hbTNdm+S7nANVTuVw3Kw0tnUjF7Ihdia/dw=
        -----END CERTIFICATE-----
        """

    static let pidCzCaPem = """
        -----BEGIN CERTIFICATE-----
        MIIC0zCCAnmgAwIBAgIUFxoZrqz1jgmJeXu6UxkuRcCgf/AwCgYIKoZIzj0EAwMw
        VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
        ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJDWjAeFw0yNTA0
        MDgyMzU0MTFaFw0zNDA3MDUyMzU0MTBaMFcxGTAXBgNVBAMMEFBJRCBJc3N1ZXIg
        Q0EgMDIxLTArBgNVBAoMJEVVREkgV2FsbGV0IFJlZmVyZW5jZSBJbXBsZW1lbnRh
        dGlvbjELMAkGA1UEBhMCQ1owWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQujURw
        lrlAeGV/8wYTl9ceasR2YM55vI9cSaZOeXpBu3v6wlEhOHvDLVxlz2zJEJrqvg37
        3DOO0RdWEvkGwf7Zo4IBITCCAR0wEgYDVR0TAQH/BAgwBgEB/wIBADAfBgNVHSME
        GDAWgBSzozJELVa05Qse3atn1+cKtbbXgDATBgNVHSUEDDAKBggrgQICAAABBzBD
        BgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9j
        cmwvcGlkX0NBX0NaXzAyLmNybDAdBgNVHQ4EFgQUs6MyRC1WtOULHt2rZ9fnCrW2
        14AwDgYDVR0PAQH/BAQDAgEGMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNv
        bS9ldS1kaWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJl
        ZmVyZW5jZS1mcmFtZXdvcmswCgYIKoZIzj0EAwMDSAAwRQIhAOWHisDphPFySZtS
        +/1Ufp5aW+Ci3w4aDSw7+EW+TD6mAiAh3/SiF2zzZybp64sG/OiwdhH2LqsizuTD
        1zFx4oCdqQ==
        -----END CERTIFICATE-----
        """

    static let pidEeCaPem = """
        -----BEGIN CERTIFICATE-----
        MIIC0jCCAnmgAwIBAgIUPP5TRFaC6GrLVVc5T83dCyunbcMwCgYIKoZIzj0EAwMw
        VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
        ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFRTAeFw0yNTA0
        MDkwMDAxMzZaFw0zNDA3MDYwMDAxMzVaMFcxGTAXBgNVBAMMEFBJRCBJc3N1ZXIg
        Q0EgMDIxLTArBgNVBAoMJEVVREkgV2FsbGV0IFJlZmVyZW5jZSBJbXBsZW1lbnRh
        dGlvbjELMAkGA1UEBhMCRUUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAR+Lkqc
        gTsK8wHwzdAgCtL54yHpe/pfAMF5BuDJ+0SQAl1E+eN2g2BelLKrHwyiiktORwI8
        tH/52pfJf+PdNcnno4IBITCCAR0wEgYDVR0TAQH/BAgwBgEB/wIBADAfBgNVHSME
        GDAWgBQ1uvSpg46wPpr1mUort5On76if2DATBgNVHSUEDDAKBggrgQICAAABBzBD
        BgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9j
        cmwvcGlkX0NBX0VFXzAyLmNybDAdBgNVHQ4EFgQUNbr0qYOOsD6a9ZlKK7eTp++o
        n9gwDgYDVR0PAQH/BAQDAgEGMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNv
        bS9ldS1kaWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJl
        ZmVyZW5jZS1mcmFtZXdvcmswCgYIKoZIzj0EAwMDRwAwRAIgN+axEyAC2Z62WkW0
        eLB5C9vZmqOf8+MNKzoB+uHjK+wCIE5fee6J0rnBkw2ZnFHpX0zxUiuDL9C5sjkw
        AbVJjmT1
        -----END CERTIFICATE-----
        """

    static let pidEuCaPem = """
        -----BEGIN CERTIFICATE-----
        MIIC0zCCAnmgAwIBAgIUXRXxkLbUM6+njr/XT0IIw/HA/uowCgYIKoZIzj0EAwMw
        VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
        ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFVTAeFw0yNTA0
        MDkwMDAzMzBaFw0zNDA3MDYwMDAzMjlaMFcxGTAXBgNVBAMMEFBJRCBJc3N1ZXIg
        Q0EgMDIxLTArBgNVBAoMJEVVREkgV2FsbGV0IFJlZmVyZW5jZSBJbXBsZW1lbnRh
        dGlvbjELMAkGA1UEBhMCRVUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARkqdLm
        wIlv+SSWr00tAIrt7EAMztgd3w9qA6qEm16yVfsLcyx2f4oIWuH45wa37J9GoNWp
        deo27VoSoNMCzxOYo4IBITCCAR0wEgYDVR0TAQH/BAgwBgEB/wIBADAfBgNVHSME
        GDAWgBRCUFC+ELgQ8J1EXI2/qxAI7ifcSTATBgNVHSUEDDAKBggrgQICAAABBzBD
        BgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9j
        cmwvcGlkX0NBX0VVXzAyLmNybDAdBgNVHQ4EFgQUQlBQvhC4EPCdRFyNv6sQCO4n
        3EkwDgYDVR0PAQH/BAQDAgEGMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNv
        bS9ldS1kaWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJl
        ZmVyZW5jZS1mcmFtZXdvcmswCgYIKoZIzj0EAwMDSAAwRQIhAIavYfC5o0VVLKfg
        TKkzzWgc09hzDMsCl3O2le2sQfG7AiA2soqAN5gtUOLQKWK00DUz22EW79rvaV+V
        JPvfdQeokA==
        -----END CERTIFICATE-----
        """

    static let pidLuCaPem = """
        -----BEGIN CERTIFICATE-----
        MIIC0zCCAnmgAwIBAgIUYGz2Xxw7UFgSmRsIkFTTBclg8fcwCgYIKoZIzj0EAwMw
        VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
        ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJMVTAeFw0yNTA0
        MDkwMDA1MzlaFw0zNDA3MDYwMDA1MzhaMFcxGTAXBgNVBAMMEFBJRCBJc3N1ZXIg
        Q0EgMDIxLTArBgNVBAoMJEVVREkgV2FsbGV0IFJlZmVyZW5jZSBJbXBsZW1lbnRh
        dGlvbjELMAkGA1UEBhMCTFUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAR6X1Nl
        EGbdWUWiUrSA/YiNpcZeI95z2MglbvISRO19YUc4GvPTevbg/Fm9MekJeHqRQO4G
        HTlBPNGM2aiBtu5Mo4IBITCCAR0wEgYDVR0TAQH/BAgwBgEB/wIBADAfBgNVHSME
        GDAWgBRcYvvRpiZ6Zz81VxQ4+AdY4Iez8DATBgNVHSUEDDAKBggrgQICAAABBzBD
        BgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9j
        cmwvcGlkX0NBX0xVXzAyLmNybDAdBgNVHQ4EFgQUXGL70aYmemc/NVcUOPgHWOCH
        s/AwDgYDVR0PAQH/BAQDAgEGMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNv
        bS9ldS1kaWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJl
        ZmVyZW5jZS1mcmFtZXdvcmswCgYIKoZIzj0EAwMDSAAwRQIgd69HgNvnIVbHg5lY
        2SzgExy72DUNCyi20An6OGNqWw4CIQDkDMDTmPd6p/aHAtYP8Jh7z/4Nb/09LxpN
        XQS72ouixA==
        -----END CERTIFICATE-----
        """

    static let pidNlCaPem = """
        -----BEGIN CERTIFICATE-----
        MIIC0jCCAnmgAwIBAgIUWcI7dH8iTcdDsGe93t9tWfdoKr0wCgYIKoZIzj0EAwMw
        VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
        ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJOTDAeFw0yNTA0
        MDkwMDA3MjZaFw0zNDA3MDYwMDA3MjVaMFcxGTAXBgNVBAMMEFBJRCBJc3N1ZXIg
        Q0EgMDIxLTArBgNVBAoMJEVVREkgV2FsbGV0IFJlZmVyZW5jZSBJbXBsZW1lbnRh
        dGlvbjELMAkGA1UEBhMCTkwwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAT0h0lj
        qQJKWwHW8sHJX3psnuCJBt/z82bO9yiy8z9CxLLXUNJj4vi8ox7itb5nXTaSlL02
        ChMl8GgXLl3lg1A3o4IBITCCAR0wEgYDVR0TAQH/BAgwBgEB/wIBADAfBgNVHSME
        GDAWgBTMrdmCj0nOwclVaiDpYcnwxnCY7jATBgNVHSUEDDAKBggrgQICAAABBzBD
        BgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9j
        cmwvcGlkX0NBX05MXzAyLmNybDAdBgNVHQ4EFgQUzK3Zgo9JzsHJVWog6WHJ8MZw
        mO4wDgYDVR0PAQH/BAQDAgEGMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNv
        bS9ldS1kaWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJl
        ZmVyZW5jZS1mcmFtZXdvcmswCgYIKoZIzj0EAwMDRwAwRAIgd24eUv61oXeE2tZQ
        /WRe28t4Q575ktymDiHPzwj5UCQCIGoSN5ntiPEa4dk8P48blwgXY74+1svzmTVT
        WYtVmCsv
        -----END CERTIFICATE-----
        """

    static let pidPtCaPem = """
        -----BEGIN CERTIFICATE-----
        MIIC3jCCAoOgAwIBAgIUZXIN5wL8XzOB1WtwPqAr6GSIm2MwCgYIKoZIzj0EAwMw
        XDEeMBwGA1UEAwwVUElEIElzc3VlciBDQSAtIFBUIDAyMS0wKwYDVQQKDCRFVURJ
        IFdhbGxldCBSZWZlcmVuY2UgSW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlBUMB4X
        DTI1MDQwODE5MzU0NFoXDTM0MDcwNTE5MzU0M1owXDEeMBwGA1UEAwwVUElEIElz
        c3VlciBDQSAtIFBUIDAyMS0wKwYDVQQKDCRFVURJIFdhbGxldCBSZWZlcmVuY2Ug
        SW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlBUMFkwEwYHKoZIzj0CAQYIKoZIzj0D
        AQcDQgAEQctevxuugp0BBrsKpxBUJfoF4t/vRgxYFh2VzklNZisO7aihVhdiXyvO
        LdZJZk7H4nbJltmhG3P+Wjb2QY8Mp6OCASEwggEdMBIGA1UdEwEB/wQIMAYBAf8C
        AQAwHwYDVR0jBBgwFoAU/Fq6x2Mh0FTKT5R5feWq08S+mJEwEwYDVR0lBAwwCgYI
        K4ECAgAAAQcwQwYDVR0fBDwwOjA4oDagNIYyaHR0cHM6Ly9wcmVwcm9kLnBraS5l
        dWRpdy5kZXYvY3JsL3BpZF9DQV9QVF8wMi5jcmwwHQYDVR0OBBYEFPxausdjIdBU
        yk+UeX3lqtPEvpiRMA4GA1UdDwEB/wQEAwIBBjBdBgNVHRIEVjBUhlJodHRwczov
        L2dpdGh1Yi5jb20vZXUtZGlnaXRhbC1pZGVudGl0eS13YWxsZXQvYXJjaGl0ZWN0
        dXJlLWFuZC1yZWZlcmVuY2UtZnJhbWV3b3JrMAoGCCqGSM49BAMDA0kAMEYCIQCs
        H7B1TjFfNI2mr3zDy2TCboDgcgiQ/Xzh5ZNfyeT3/gIhAK7frwdyKHyhe1ruHEhK
        6bZ/eovd4pE/w+WDPBOqYcIM
        -----END CERTIFICATE-----
        """

    static let pidUtCaPem = """
        -----BEGIN CERTIFICATE-----
        MIIC3TCCAoOgAwIBAgIUEwybFc9Jw+az3r188OiHDaxCfHEwCgYIKoZIzj0EAwMw
        XDEeMBwGA1UEAwwVUElEIElzc3VlciBDQSAtIFVUIDAyMS0wKwYDVQQKDCRFVURJ
        IFdhbGxldCBSZWZlcmVuY2UgSW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlVUMB4X
        DTI1MDMyNDIwMjYxNFoXDTM0MDYyMDIwMjYxM1owXDEeMBwGA1UEAwwVUElEIElz
        c3VlciBDQSAtIFVUIDAyMS0wKwYDVQQKDCRFVURJIFdhbGxldCBSZWZlcmVuY2Ug
        SW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlVUMFkwEwYHKoZIzj0CAQYIKoZIzj0D
        AQcDQgAEesDKj9rCIcrGj0wbSXYvCV953bOPSYLZH5TNmhTz2xa7VdlvQgQeGZRg
        1PrF5AFwt070wvL9qr1DUDdvLp6a1qOCASEwggEdMBIGA1UdEwEB/wQIMAYBAf8C
        AQAwHwYDVR0jBBgwFoAUYseURyi9D6IWIKeawkmURPEB08cwEwYDVR0lBAwwCgYI
        K4ECAgAAAQcwQwYDVR0fBDwwOjA4oDagNIYyaHR0cHM6Ly9wcmVwcm9kLnBraS5l
        dWRpdy5kZXYvY3JsL3BpZF9DQV9VVF8wMi5jcmwwHQYDVR0OBBYEFGLHlEcovQ+i
        FiCnmsJJlETxAdPHMA4GA1UdDwEB/wQEAwIBBjBdBgNVHRIEVjBUhlJodHRwczov
        L2dpdGh1Yi5jb20vZXUtZGlnaXRhbC1pZGVudGl0eS13YWxsZXQvYXJjaGl0ZWN0
        dXJlLWFuZC1yZWZlcmVuY2UtZnJhbWV3b3JrMAoGCCqGSM49BAMDA0gAMEUCIQCe
        4R9rO4JhFp821kO8Gkb8rXm4qGG/e5/Oi2XmnTQqOQIgfFs+LDbnP2/j1MB4rwZ1
        FgGdpr4oyrFB9daZyRIcP90=
        -----END CERTIFICATE-----
        """

    static let authologicVerifierPem = """
        -----BEGIN CERTIFICATE-----
        MIICWjCCAf+gAwIBAgIURMxgfdZVyDkuuDk+5EF0O6Eq56MwCgYIKoZIzj0EAwIw
        ejELMAkGA1UEBhMCUEwxFDASBgNVBAgMC01hem93aWVja2llMQ8wDQYDVQQHDAZX
        YXJzYXcxEzARBgNVBAoMCkF1dGhvbG9naWMxETAPBgNVBAsMCERldmVsIENBMRww
        GgYDVQQDDBNBdXRob2xvZ2ljIERldmVsIENBMB4XDTI2MDgxNDExNDcyN1oXDTM2
        MDgxMTExNDcyN1owejELMAkGA1UEBhMCUEwxFDASBgNVBAgMC01hem93aWVja2ll
        MQ8wDQYDVQQHDAZXYXJzYXcxEzARBgNVBAoMCkF1dGhvbG9naWMxETAPBgNVBAsM
        CERldmVsIENBMRwwGgYDVQQDDBNBdXRob2xvZ2ljIERldmVsIENBMFkwEwYHKoZI
        zj0CAQYIKoZIzj0DAQcDQgAEQNrndgweAUWh9FBYRBAeSZH7MSX5XQk/HEYuei25
        NKvLYd35wcDUXPO5OS/oxZWts86lIW3YuLMjZsq4rybGf6NjMGEwHwYDVR0jBBgw
        FoAUokoQrmgCxZsfAKpAcK/iTd2q9pkwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8B
        Af8EBAMCAQYwHQYDVR0OBBYEFKJKEK5oAsWbHwCqQHCv4k3dqvaZMAoGCCqGSM49
        BAMCA0kAMEYCIQD+u+EPuNX47F9Fhu9Okj6rs6MdGAXkMPBz8dIgO0CsLgIhAIXd
        VWuOtBE3oUD8rAOwlVMDD0Mh/viqNJ5MbS9VB2q+
        -----END CERTIFICATE-----
        """

    static let authologicReaderPem = """
        -----BEGIN CERTIFICATE-----
        MIIB3DCCAYOgAwIBAgIUcMNDYnHDDVHpOP3Hr7ICtlcYLuwwCgYIKoZIzj0EAwIw
        RDEgMB4GA1UEAwwXQXV0aG9sb2dpYyBWZXJpZmllciBEZXYxEzARBgNVBAoMCkF1
        dGhvbG9naWMxCzAJBgNVBAYTAlBMMB4XDTI1MTAyODA4MDUxMVoXDTI2MTAyODA4
        MDUxMVowRDEgMB4GA1UEAwwXQXV0aG9sb2dpYyBWZXJpZmllciBEZXYxEzARBgNV
        BAoMCkF1dGhvbG9naWMxCzAJBgNVBAYTAlBMMFkwEwYHKoZIzj0CAQYIKoZIzj0D
        AQcDQgAEkbbJTcjOozGhwx/6GtH1hh5BWbRd/CZL95Cy0LUj9gQmHEA9DzBzqhmz
        UzD9DCVjTeaJ7smnyMIEkotFQSANb6NTMFEwHQYDVR0OBBYEFAtXR3HJegYDnF7e
        dbc3tiwfzytmMB8GA1UdIwQYMBaAFAtXR3HJegYDnF7edbc3tiwfzytmMA8GA1Ud
        EwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDRwAwRAIhAJ0ak5Vluoj6irVz5CjuAcUe
        ivkGqOZBNz0HMNc86pgvAh9VgANGh61oaZL5S9FfCr6PtcBABmmIo3ejZwDdLCms
        -----END CERTIFICATE-----
        """

    static let webuildRpRootPem = """
        -----BEGIN CERTIFICATE-----
        MIIDfjCCAySgAwIBAgIQRAL20BMQkFskig+t5XJgmDAKBggqhkjOPQQDAjCByzEL
        MAkGA1UEBhMCTkwxDzANBgNVBAgTBkhlc3NlbjEPMA0GA1UEBxMGTGFuZ2VuMRcw
        FQYDVQQJEw5SaGVpbnN0cmFzc2UgNTEOMAwGA1UEERMFNjMyMjUxQTA/BgNVBAoT
        OFdFQlVJTEQgLSBXUCA0IC0gR3JvdXAgNSAtIFRydXN0IFJlZ2lzdHJ5IEluZnJh
        c3RydWN0dXJlMS4wLAYDVQQDEyVodHRwczovL3dlYnVpbGQtY29uc29ydGl1bS5n
        aXRodWIuaW8vMB4XDTI2MDkxNDE0MDczNVoXDTI5MDMxOTIzNTk1OVowgcsxCzAJ
        BgNVBAYTAk5MMQ8wDQYDVQQIEwZIZXNzZW4xDzANBgNVBAcTBkxhbmdlbjEXMBUG
        A1UECRMOUmhlaW5zdHJhc3NlIDUxDjAMBgNVBBETBTYzMjI1MUEwPwYDVQQKEzhX
        RUJVSUxEIC0gV1AgNCAtIEdyb3VwIDUgLSBUcnVzdCBSZWdpc3RyeSBJbmZyYXN0
        cnVjdHVyZTEuMCwGA1UEAxMlaHR0cHM6Ly93ZWJ1aWxkLWNvbnNvcnRpdW0uZ2l0
        aHViLmlvLzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABMdSOu3aq4Q7RYMAYTMx
        f2rkuqR2HMNXBmMaKagXqwWPB3a0EicH0KFACmqT1mvfGInOb62B+MPT83nGkMN2
        yoSjgecwgeQwDgYDVR0PAQH/BAQDAgGGMBEGA1UdJQQKMAgGBgQAkTcDADAPBgNV
        HRMBAf8EBTADAQH/MB0GA1UdDgQWBBRp9jiDJg2JDnzX4ZKv3waZ6LYAVjAfBgNV
        HSMEGDAWgBRp9jiDJg2JDnzX4ZKv3waZ6LYAVjBuBgNVHREEZzBlgQ9pbmZvQGlk
        dW5pb24uZXWGUmh0dHBzOi8vYXBpLndhbGxldC5pZHVuaW9uLmluZm8vYXBpL3Yx
        L2lzc3Vlci8wMWEwYTAzZS00NWFkLTdjMjQtOTM5OS05M2Y0Y2E3MzEyNjkwCgYI
        KoZIzj0EAwIDSAAwRQIhAKlIRTuWT+K1SU2hA0w5PCDDhgsbyWrAt6J1gvcOcxsH
        AiAwrUu7Q8mZxYlhUZ72F4vfj6wRMI5qxY7TFJiWS50z0w==
        -----END CERTIFICATE-----
        """

    /// WeBuild Raidiam "EUDI Sandbox Registration Certificate Signer" root (self-signed).
    /// Source: ETSI TL https://tl-api.dev.idunion.info/api/v1/DblnEVuf/etsi/tl.xml. Valid until 2027-10-18.
    static let webuildRaidiamRootPem = """
        -----BEGIN CERTIFICATE-----
        MIIDQzCCAiugAwIBAgIFXpo73NswDQYJKoZIhvcNAQELBQAwYzELMAkGA1UEBhMC
        R0IxHTAbBgNVBAoMFFJhaWRpYW0gU2VydmljZXMgTHRkMTUwMwYDVQQDDCxFVURJ
        IFNhbmRib3ggUmVnaXN0cmF0aW9uIENlcnRpZmljYXRlIFNpZ25lcjAeFw0yNjA5
        MTgxNjAwNDBaFw0yNzEwMTgxNjAwNDBaMGMxCzAJBgNVBAYTAkdCMR0wGwYDVQQK
        DBRSYWlkaWFtIFNlcnZpY2VzIEx0ZDE1MDMGA1UEAwwsRVVESSBTYW5kYm94IFJl
        Z2lzdHJhdGlvbiBDZXJ0aWZpY2F0ZSBTaWduZXIwggEiMA0GCSqGSIb3DQEBAQUA
        A4IBDwAwggEKAoIBAQDaN+7O9dDWMEtuICRcBYzKhAIp74Sa9XcOFaAYkvhiFPAo
        kT3rpjDiDPBKfR7qK8oIPQwv3zz1Z9MwxYvLRiks9NIGvDlLxIXYCbd6JYVK9BZx
        Rvm3fFZTdpLlT6GEmgwolyhJFVTg2WfgrAmVB9gUU9VC1v7dLLKgmApniSJlYHbQ
        TW0y46oF9JRSKxc6KXR3qMsa063l5LGa0ouRs5j3Dec3vaJHAI6CzLiJ5ZEkZCuY
        4KZrgda7nqmTzB2QRmobdgEMzOAMbiTxJyDM/NhOHEQWkArrL+8b84drQThLIsEp
        VFyFGcji7sixC7av5AYZKb+FdmqoBEeDmVLgjqjJAgMBAAEwDQYJKoZIhvcNAQEL
        BQADggEBAGYHa14y8YJYZIHQAmcYuAoUr9GXqmIFHyNXkBCcZovq4y3iGWn8t3Ru
        2waETu/71E5RuSoH+ZiFRG9sGn3nIJXTeTojWB9iYNYz67G8sRmBzO2Af18uVLry
        txb45J+F36ILlwBtIWSeEVCak/aRZfVEUIa9mRERRzy4DpAO8qgcFX2FRUGVKXUk
        Y0kZsOz19x0TDpsuIrI4a2ctowTlzn1BuzbtoyC8sTLybLCd4IxlrVpwAMUCUbys
        tFKDtZu+QP7cVmIfMHjIN7bryyFdqdOf93A251S3NdFU2fMw80anIJhup4zLZ9CG
        gu43JYSWIGqKFtXzQ2NGETUJWclnOKM=
        -----END CERTIFICATE-----
        """

    /// Test wallet-gateway verifier leaf and issuing CA for signed Request Objects.
    /// SAN DNS `mc.test.walletgateway.com`. Leaf valid until 2027-01-13.
    /// The root is omitted: its ECDSA AlgorithmIdentifier has NULL parameters, which
    /// RFC 5758 section 3.2 forbids and the iOS parser rejects. Requests must omit it from x5c.
    static let walletGatewayTestLeafPem = """
        -----BEGIN CERTIFICATE-----
        MIIDTDCCAvOgAwIBAgIIDzMLWn1TTSkwCgYIKoZIzj0EAwIwgYAxEzARBgoJkiaJ
        k/IsZAEZFgN0c3QxGjAYBgoJkiaJk/IsZAEZFgptY2ludGVybmFsMRMwEQYKCZIm
        iZPyLGQBGRYDcGtpMRMwEQYDVQQKEwpNYXN0ZXJjYXJkMSMwIQYDVQQDExpNQyBJ
        bnRlcm5hbCBUU1QgU3ViIENBMSBHMTAeFw0yNjEwMTUxMTM5NTdaFw0yNzAxMTMx
        MTM5NTdaMIGwMRMwEQYKCZImiZPyLGQBGRYDdHN0MRowGAYKCZImiZPyLGQBGRYK
        bWNpbnRlcm5hbDELMAkGA1UEBhMCVVMxETAPBgNVBAgTCE1pc3NvdXJpMRQwEgYD
        VQQHEwtTYWludCBMb3VpczEdMBsGA1UEChMUTWFzdGVyY2FyZCBXb3JsZHdpZGUx
        KDAmBgNVBAMTH01hc3RlcmNhcmQgVmVyaWZpY2F0aW9uIFNlcnZpY2UwWTATBgcq
        hkjOPQIBBggqhkjOPQMBBwNCAASveanYl46jFem1BxGlQRpsA08l3F4JVvuAk/g4
        XC4DmIH9VFdnTX+QiD2fR6bWzucRoF919OYInYOtYT06L9LHo4IBIzCCAR8wRwYD
        VR0RBEAwPoIZbWMudGVzdC53YWxsZXRnYXRld2F5LmNvbYYhaHR0cHM6Ly9tYy50
        ZXN0LndhbGxldGdhdGV3YXkuY29tMA4GA1UdDwEB/wQEAwIAgDAMBgNVHRMBAf8E
        AjAAMB0GA1UdDgQWBBTNXoPd5X4jyvTA0ekiBtqTVEVeRTAfBgNVHSMEGDAWgBQz
        oCCQj4uO3ydfRMx5xjPu1LryqzATBgNVHSUEDDAKBggrBgEFBQcDAjBhBgNVHR8E
        WjBYMFagVKBShlBodHRwOi8vY3JsZHAuZWNtcy5tY2xvY2FsLmludDoxMzUzNi8z
        M2EwMjA5MDhmOGI4ZWRmMjc1ZjQ0Y2M3OWM2MzNlZWQ0YmFmMmFiLmNybDAKBggq
        hkjOPQQDAgNHADBEAiAHZSNOrAiMMvvArE93ysDZHBozcRBLFL/CP/HRZWdffAIg
        fU6s/qGN0/W0vGjvYxtOlQ4/JZbr2QQl4fecA722jNI=
        -----END CERTIFICATE-----
        """

    static let walletGatewayTestSubCaPem = """
        -----BEGIN CERTIFICATE-----
        MIIC0jCCAligAwIBAgIIDzMLWm4GFc8wCgYIKoZIzj0EAwMwgYAxEzARBgoJkiaJ
        k/IsZAEZFgN0c3QxGjAYBgoJkiaJk/IsZAEZFgptY2ludGVybmFsMRMwEQYKCZIm
        iZPyLGQBGRYDcGtpMRMwEQYDVQQKEwpNYXN0ZXJjYXJkMSMwIQYDVQQDExpNQyBJ
        bnRlcm5hbCBUU1QgUm9vdCBDQSBHMTAeFw0yMjA4MDMwODU2MzBaFw0zMjA3MzEw
        ODU2MzBaMIGAMRMwEQYKCZImiZPyLGQBGRYDdHN0MRowGAYKCZImiZPyLGQBGRYK
        bWNpbnRlcm5hbDETMBEGCgmSJomT8ixkARkWA3BraTETMBEGA1UEChMKTWFzdGVy
        Y2FyZDEjMCEGA1UEAxMaTUMgSW50ZXJuYWwgVFNUIFN1YiBDQTEgRzEwWTATBgcq
        hkjOPQIBBggqhkjOPQMBBwNCAAQjC0fzby0qOaD+1vw4sNBm5epmJ7I2nmFEtG/Q
        HUMit0xVZ9QtkrCWamV9NgVkzXML4Flu8fYMmzX2iXeaAv7uo4G5MIG2MA4GA1Ud
        DwEB/wQEAwIAhjAdBgNVHQ4EFgQUM6AgkI+Ljt8nX0TMecYz7tS68qswDwYDVR0T
        BAgwBgEB/wIBADAfBgNVHSMEGDAWgBQKCorpqpXd2qvlKDnVs6Suq10jhTBTBgNV
        HR8ETDBKMEigRqBEhkJodHRwOi8vY3JsZHAuZWNtcy5tY2xvY2FsLmludDoxMzUz
        Ni9tYy1pbnRlcm5hbC10c3Qtcm9vdC1jYS1nMS5jcmwwCgYIKoZIzj0EAwMDaAAw
        ZQIxAIF3EU2ON1T8KHeusNw1hDiqZT5nG/bAR1bcaOIiAM5iU5r60qT5CKytMViq
        fqaS+wIwIgcz6N/9B5E29/SssaK3UgRqWx/KeQDTBahLO1UpAf1GVVbBWP6ArR/8
        n1Jc/HXi
        -----END CERTIFICATE-----
        """

    /// PEM trust anchors passed into `WalletConfiguration.clientIDTrustConfiguration`.
    static let x509TrustAnchorPems: [String] = [
        verifier2ExampleLeafPem,
        pidCzCaPem,
        pidEeCaPem,
        pidEuCaPem,
        pidLuCaPem,
        pidNlCaPem,
        pidPtCaPem,
        pidUtCaPem,
        authologicVerifierPem,
        authologicReaderPem,
        webuildRpRootPem,
        webuildRaidiamRootPem,
        walletGatewayTestLeafPem,
        walletGatewayTestSubCaPem,
    ]

    static var clientIDTrustConfiguration: WalletClientIDTrustConfiguration {
        WalletClientIDTrustConfiguration(x509TrustAnchorsPEM: x509TrustAnchorPems)
    }
}
