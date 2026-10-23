package id.walt.ktorauthnz.valkey

import org.junit.jupiter.api.Assumptions.assumeTrue

internal fun valkeyTestPort(): Int {
    val configured = System.getenv("VALKEY_TEST_PORT")
    assumeTrue(configured != null, "VALKEY_TEST_PORT is not set; the Valkey backend is unavailable")
    return requireNotNull(configured?.toIntOrNull()?.takeIf { it in 1..65535 }) {
        "VALKEY_TEST_PORT must be an integer in 1..65535"
    }
}
