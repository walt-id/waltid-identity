package id.walt.cli.commands

import java.io.File

object TestCryptoFixtures {
    val holderKey = File("src/jvmTest/resources/key/ed25519_by_waltid_pvt_key.jwk")
    const val holderDid = "did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV"

    val mdocIssuerJwk = """
        {
          "kty": "EC",
          "d": "-wSIL_tMH7-mO2NAfHn03I8ZWUHNXVzckTTb96Wsc1s",
          "crv": "P-256",
          "kid": "sW5yv0UmZ3S0dQuUrwlR9I3foREBHHFwXhGJGqGEVf0",
          "x": "Pzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5U",
          "y": "6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZo"
        }
    """.trimIndent()

    const val mdocIssuerCertificate =
        "MIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M"
}
