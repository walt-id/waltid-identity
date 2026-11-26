package id.walt.itb

internal object ItbDcScriptFixtures {
    val issuance = """
        const offerEndpoint = 'https://dev-i4mlab.aegean.gr/rfc-issuer/vci/dc-api/offer';
        const scenario = 'pid-authorization-code';
        const credentialType = 'urn:eu.europa.ec.eudi:pid:1';
        const protocol = 'openid4vci-v1';
    """.trimIndent()

    val presentation = """
        async function requestCredential() {
          const presentationRequestEndpoint = 'https://dev-i4mlab.aegean.gr/rfc-issuer/vp/dc-api/request';
          const validationSessionId = '00000000-0000-0000-0000-000000000001';
          const profile = 'pid-basic';
          const dcApiProtocol = 'openid4vp-v1-signed';
        }
    """.trimIndent()

    val payment = """
        const requestEndpoint = 'https://dev-i4mlab.aegean.gr/rfc-issuer/vp/dc-api/request';
        const sessionId = 'payment-session';
        const profile = 'ts12-iban';
        const attestationType = 'sca-iban';
        const dcApiProtocol = 'openid4vp-v1-signed';
        const body = {
            merchant: 'Coffee Shop', payee_id: 'shop-42', currency: 'EUR',
            amount: '12.34', transaction_id: 'tx-dc-api-iban-001'
        };
    """.trimIndent()
}
