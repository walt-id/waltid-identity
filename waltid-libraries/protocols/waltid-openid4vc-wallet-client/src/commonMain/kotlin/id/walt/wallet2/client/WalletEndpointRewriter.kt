package id.walt.wallet2.client

fun interface WalletEndpointRewriter {
    fun rewrite(value: String): String

    object Noop : WalletEndpointRewriter {
        override fun rewrite(value: String): String = value
    }

    class HostRewriter(
        private val hostRewrites: Map<String, String>,
    ) : WalletEndpointRewriter {
        override fun rewrite(value: String): String =
            hostRewrites.entries.fold(value) { current, (from, to) ->
                current.replace(from, to)
            }
    }

    companion object {
        fun androidEmulatorLocalhost(): WalletEndpointRewriter = HostRewriter(
            mapOf(
                "waltid.enterprise.localhost" to "10.0.2.2",
                "enterprise.localhost" to "10.0.2.2",
            )
        )
    }
}
