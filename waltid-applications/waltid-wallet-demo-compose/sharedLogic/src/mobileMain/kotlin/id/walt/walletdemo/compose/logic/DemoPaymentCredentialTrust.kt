package id.walt.walletdemo.compose.logic

import id.walt.wallet2.consent.PaymentCredentialIssuer

/**
 * Public verification material pinned independently of any credential header.
 * The issuer string must equal the credential `iss` claim, and the JWK must be the
 * public half of the key that signed it.
 */
internal val demoPaymentCredentialIssuers = listOf(
    PaymentCredentialIssuer(
        issuer = "https://issuer2.demo.walt.id/openid4vci",
        publicJwkJson = """{"kty":"EC","crv":"P-256","x":"G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM","y":"ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"}""",
    ),
    PaymentCredentialIssuer(
        issuer = "https://waltid.enterprise.test.waltid.cloud/v2/waltid.bank-tenant.issuer2/issuer-service-api/openid4vci",
        publicJwkJson = """{"kty":"EC","crv":"P-256","x":"dUpq6W-c6yLniF3k5Lj5j4rFysYXmnHTSK_aOb2JV_A","y":"urKlUBnHoHIeBP9YuCj9ZwVBewS4yNkaT1J64gQLnEw"}""",
    ),
)
