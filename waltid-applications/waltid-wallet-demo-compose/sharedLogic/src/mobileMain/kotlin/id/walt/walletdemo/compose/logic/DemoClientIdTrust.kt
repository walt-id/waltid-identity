@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.walletdemo.compose.logic

import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.x509.CertificateDer

/**
 * Example verifier trust anchors for the Compose demo.
 *
 * OpenID4VP `x509_san_dns` and `x509_hash` clients fail closed unless the wallet pins at least one
 * X.509 trust anchor. `decentralized_identifier` clients still authenticate through DID resolution
 * and do not use this list.
 *
 * The PEMs match walt.id Verifier2 demo material. Replace or append entries in
 * [x509TrustAnchorPems] with your own CAs (or pinned leaves) before shipping a production wallet.
 */
object DemoClientIdTrust {

    /**
     * Self-signed leaf from `waltid-verifier-api2/config/verifier-service.conf` (`x5c`).
     *
     * SAN DNS `verifier.example.com`. Pair with:
     * - `x509_san_dns:verifier.example.com` (commented next to `clientId: "verifier2"`)
     * - `x509_hash:OPpTDyXlg6WRu2-Qn4rpQcA9uVqSrNExCS8kCYUe09A` (SHA-256 of this leaf)
     *
     * Demo material only, not a production CA. Valid until 2026-10-14.
     * This leaf has no `keyUsage` extension, so client-auth usage checks may still reject it even
     * when it is pinned. Production wallets should pin a CA and use client-auth leaves.
     */
    val VERIFIER2_EXAMPLE_LEAF_PEM = """
        -----BEGIN CERTIFICATE-----
        MIIBVzCB/aADAgECAggNKZAvUrtimzAKBggqhkjOPQQDAjAfMR0wGwYDVQQDDBR2
        ZXJpZmllci5leGFtcGxlLmNvbTAeFw0yNTEwMTQwNjI0MjBaFw0yNjEwMTQwNjI0
        MjBaMB8xHTAbBgNVBAMMFHZlcmlmaWVyLmV4YW1wbGUuY29tMFkwEwYHKoZIzj0C
        AQYIKoZIzj0DAQcDQgAEG/TgBc0BkmMipiQ/6gkamIn3mmp7hcTrZuyrLTmknP1W
        RExl1dhdIx9/kAkuuceI3THkxXq7/y+sBzK0ZR7jPqMjMCEwHwYDVR0RBBgwFoIU
        dmVyaWZpZXIuZXhhbXBsZS5jb20wCgYIKoZIzj0EAwIDSQAwRgIhAOu0RGM6BjVQ
        UepeLBogw+ZD3MQ9vFppbPIGMPjtn/qdAiEAttfdfyXHfzJ2tr+Pczyckzv3NlM4
        3461cvP96sIzOQA=
        -----END CERTIFICATE-----
    """.trimIndent()

    /**
     * walt.id Verifier CA used by OpenID4VP conformance (`TestKeyMaterial.VERIFIER_CA_PEM`).
     *
     * Pin this when the Request Object leaf is issued by that CA for the same
     * `verifier.example.com` identity (CA-signed rather than the self-signed `x5c` above).
     * Valid until 2036-05-16.
     */
    val WALTID_VERIFIER_CA_PEM = """
        -----BEGIN CERTIFICATE-----
        MIIBlzCCAT2gAwIBAgIUUffF2b0tyOxgDu7q+kMpwY3pfNUwCgYIKoZIzj0EAwIw
        MDEcMBoGA1UEAwwTd2FsdC5pZCBWZXJpZmllciBDQTEQMA4GA1UECgwHd2FsdC5p
        ZDAeFw0yNjA1MTkwNDA4MTZaFw0zNjA1MTYwNDA4MTZaMDAxHDAaBgNVBAMME3dh
        bHQuaWQgVmVyaWZpZXIgQ0ExEDAOBgNVBAoMB3dhbHQuaWQwWTATBgcqhkjOPQIB
        BggqhkjOPQMBBwNCAAQnFYwN1ypusrveHnOwC2ZFBT6PosWX5l1caoRPoziV8jn8
        EJx0uKD5RHC0p1CbYGHBqE74YUw7xlydTT1jXfCsozUwMzASBgNVHRMBAf8ECDAG
        AQH/AgEAMB0GA1UdDgQWBBRdho/7KlGi74YmeLFqLMfbH6cSkzAKBggqhkjOPQQD
        AgNIADBFAiEAudxJV83uP0g5zLXI85ExlkRMKZI52mkBkk074ST2KPACIEsFnJDr
        xtEgGXjHNMaUj7FOpC4tJyGlg2DSpXSOlCkl
        -----END CERTIFICATE-----
    """.trimIndent()


    /* CAs from the EUDI Reference Wallet */

    val PID_CZ_CA_PEM = """
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
    """.trimIndent()

    val PID_EE_CA_PEM = """
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
    """.trimIndent()


    val PID_EU_CA_PEM = """
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
    """.trimIndent()

    val PID_LU_CA_PEM = """
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
    """.trimIndent()

    val PID_NL_CA_PEM = """
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
    """.trimIndent()

    val PID_PT_CA_PEM = """
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
    """.trimIndent()

    val PID_UT_CA_PEM = """
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
    """.trimIndent()

    /**
     * PEM trust anchors passed into [id.walt.wallet2.mobile.MobileWalletFactory.create].
     *
     * Append more PEM strings to allow additional `x509_san_dns` / `x509_hash` verifiers:
     *
     * ```
     * """
     * -----BEGIN CERTIFICATE-----
     * ...your organisation's CA...
     * -----END CERTIFICATE-----
     * """.trimIndent(),
     * ```
     */
    val x509TrustAnchorPems: List<String> = listOf(
        VERIFIER2_EXAMPLE_LEAF_PEM,
        WALTID_VERIFIER_CA_PEM,
        PID_CZ_CA_PEM,
        PID_EE_CA_PEM,
        PID_EU_CA_PEM,
        PID_LU_CA_PEM,
        PID_NL_CA_PEM,
        PID_PT_CA_PEM,
        PID_UT_CA_PEM,
    )

    val configuration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(
        x509TrustAnchors = x509TrustAnchorPems.map(CertificateDer::fromPEMEncodedString),
    )
}
