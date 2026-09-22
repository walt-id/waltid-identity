package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.Verification2Session
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bounds on the in-memory session store must be configurable.
 *
 * They were introduced as constants, which left the one number an operator needs after an out-of-memory incident
 * reachable only by rebuilding the service. The right ceiling is a property of the deployment: a session's cost
 * is set by the credential presented to it, so 2,000 sessions is about 16 MB of small credentials or 3.3 GB of
 * mdocs carrying a 230 KB portrait.
 *
 * Loading goes through [ConfigManager] and a real HOCON file rather than constructing
 * [OSSVerifier2ServiceConfig] directly, because the failure this guards against is a key that does not bind -
 * a renamed or misspelled field would leave the constructed-object version of this test passing while the
 * service silently kept its defaults.
 */
class InMemorySessionStoreConfigTest {

    private val tempFiles = mutableListOf<Path>()

    @AfterTest
    fun cleanup() {
        System.clearProperty("config.file.verifier-service")
        ConfigManager.preclear()
        tempFiles.forEach { it.deleteIfExists() }
        tempFiles.clear()
    }

    @Test
    fun `configured limits are read from the config file`() {
        loadConfig(
            """
            maxInMemorySessions = 7
            maxInMemoryBytes = 1048576
            """.trimIndent()
        )

        val repository = inMemorySessionRepositoryFor(ConfigManager.getConfig<OSSVerifier2ServiceConfig>())

        assertEquals(7, repository.maxSessions)
        assertEquals(1024L * 1024, repository.maxRetainedBytes)
    }

    @Test
    fun `omitted limits fall back to the documented defaults`() {
        loadConfig("")

        val config = ConfigManager.getConfig<OSSVerifier2ServiceConfig>()
        assertEquals(null, config.maxInMemorySessions)
        assertEquals(null, config.maxInMemoryBytes)

        val repository = inMemorySessionRepositoryFor(config)

        assertEquals(DEFAULT_MAX_IN_MEMORY_SESSIONS, repository.maxSessions)
        assertEquals(DEFAULT_MAX_IN_MEMORY_BYTES, repository.maxRetainedBytes)
    }

    /** No configuration at all is the embedded and test case, and must not fail. */
    @Test
    fun `absent configuration falls back to the documented defaults`() {
        val repository = inMemorySessionRepositoryFor(null)

        assertEquals(DEFAULT_MAX_IN_MEMORY_SESSIONS, repository.maxSessions)
        assertEquals(DEFAULT_MAX_IN_MEMORY_BYTES, repository.maxRetainedBytes)
    }

    /**
     * The configured count must actually bound the store, not merely be recorded on it - the defect being fixed
     * was a limit that existed but was never enforced.
     */
    @Test
    fun `a configured session count is enforced`() = runTest {
        loadConfig("maxInMemorySessions = 3")

        val repository = inMemorySessionRepositoryFor(ConfigManager.getConfig<OSSVerifier2ServiceConfig>())
        repeat(10) { repository.create(session("configured-count-$it")) }

        assertEquals(3, repository.size)
        assertTrue(repository.unexpiredEvictions >= 7, "evictions: ${repository.unexpiredEvictions}")
    }

    /**
     * The byte budget is the knob that matters after an out-of-memory incident, so a configured value must bound
     * the store even while the session count stays far below its own ceiling.
     */
    @Test
    fun `a configured byte budget is enforced independently of the session count`() = runTest {
        loadConfig(
            """
            maxInMemorySessions = 1000
            maxInMemoryBytes = 40960
            """.trimIndent()
        )

        val repository = inMemorySessionRepositoryFor(ConfigManager.getConfig<OSSVerifier2ServiceConfig>())
        repeat(20) { repository.create(session("configured-bytes-$it")) }

        // 8 KiB per empty session against a 40 KiB budget, so five fit and the count ceiling never applies.
        assertTrue(repository.size <= 5, "sessions retained: ${repository.size}")
        assertTrue(repository.retainedBytes <= 40960, "retained: ${repository.retainedBytes}")
        assertTrue(repository.unexpiredEvictions > 0, "nothing was evicted")
    }

    private fun session(id: String) = Verification2Session(
        id = id,
        setup = CrossDeviceFlowSetup(
            core = GeneralFlowConfig(
                dcqlQuery = Json.decodeFromString(
                    """{"credentials":[{"id":"stub","format":"dc+sd-jwt","meta":{"vct_values":["https://example.com/stub"]}}]}"""
                ),
            ),
        ),
        authorizationRequest = AuthorizationRequest(clientId = "https://verifier.example.com"),
        authorizationRequestUrl = Url("openid4vp://authorize?client_id=test"),
        requestMode = Verification2Session.RequestMode.URL_ENCODED,
        status = Verification2Session.VerificationSessionStatus.ACTIVE,
    )

    private fun loadConfig(extraLines: String) {
        val configFile = Files.createTempFile("verifier-service", ".conf")
        tempFiles.add(configFile)
        Files.writeString(
            configFile,
            """
            urlPrefix = "http://localhost:7003/verification-session"
            urlHost = "openid4vp://authorize"
            """.trimIndent() + "\n" + extraLines + "\n"
        )
        System.setProperty("config.file.verifier-service", configFile.toString())
        // Cleared first, because ConfigManager is process-wide and rejects a second registration of the same name
        // with "A configuration with the name ... already exists". Another test class in the same JVM registers
        // verifier-service too, so this passed when run alone and failed in the suite - the registration order is
        // not this test's business, and depending on it would make the failure reappear at random.
        ConfigManager.preclear()
        ConfigManager.registerConfig("verifier-service", OSSVerifier2ServiceConfig::class)
        ConfigManager.loadConfigs()
    }
}
