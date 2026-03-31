package id.walt.openid4vp.conformance

/*class WalletPresentConformanceTests {

    companion object {
        val verifier2UrlPrefix: String = "https://verifier2.localhost/verification-session" // "https://xyz.ngrok-free.app/verification-session"
        val conformanceHost: String = "localhost.emobix.co.uk" // "conformance.waltid.cloud"
        val conformancePort: Int = 8443 // 443

        val conformanceServerVersionResult = runBlocking {
            runCatching {
                ConformanceInterface(conformanceHost, conformancePort).getServerVersion()
            }.onFailure {
                println("Error getting server version: $it")
            }
        }
        @JvmStatic
        val isConformanceAvailable = conformanceServerVersionResult.isSuccess
    }

    @Test
    @EnabledIf("isConformanceAvailable")
    fun runVerifier2ConformanceTests() = runTest(timeout = 5.minutes) {
        ConformanceTestRunner(
            verifier2UrlPrefix, conformanceHost, conformancePort
        ).run()
    }

}*/
