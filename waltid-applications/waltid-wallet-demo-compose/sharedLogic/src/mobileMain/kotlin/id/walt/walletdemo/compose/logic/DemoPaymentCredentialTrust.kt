package id.walt.walletdemo.compose.logic

import id.walt.wallet2.consent.PaymentCredentialIssuer

/** Public verification material pinned independently from the shipped Issuer2 demonstration profile. */
internal val demoPaymentCredentialIssuers = listOf(PaymentCredentialIssuer(
    issuer = "https://issuer2.demo.walt.id/openid4vci",
    publicJwkJson = """{"kty":"EC","crv":"P-256","x":"G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM","y":"ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"}""",
))
