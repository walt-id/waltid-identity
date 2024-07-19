import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.*
import stats.StatsAggregator

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
interface ComponentTest {
    private val logger: KLogger
        get() = KotlinLogging.logger {}
    private val stats: StatsAggregator
        get() = StatsAggregator()

    @BeforeAll
    fun setup() {
        logger.info { "Starting tests" }
    }

    @AfterAll
    fun tearDown() {
        logger.info { "Stopping tests" }
    }

    @BeforeEach
    fun beforeEach(testInfo: TestInfo) {
        logger.info { "Starting test" }
    }

    @AfterEach
    fun afterEach(testInfo: TestInfo) {
        logger.info { "Stopping test" }
    }
}