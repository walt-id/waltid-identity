package id.walt

import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret
import java.time.Clock
import kotlin.test.assertTrue

/** A well-formed wrong code, including when the request crosses a 30-second TOTP boundary. */
internal suspend fun <T> withIncorrectTotp(secret: String, request: suspend (String) -> T): T {
    val clock = Clock.systemUTC()
    val started = clock.instant()
    val generator = TOTPGenerator(clock = Clock.fixed(started, clock.zone))
    val step = (started.epochSecond - generator.startTime) / generator.timeStepSeconds
    val excluded = generator.generate(TOTPSecret.fromBase32EncodedString(secret), delaySteps = 1, futureSteps = 2)
        .map { it.value }.toSet()
    val incorrect = (0..999999).asSequence().map { it.toString().padStart(6, '0') }.first { it !in excluded }

    return request(incorrect).also {
        // Production uses the real clock. Do not credit a rejection outside the interval checked above.
        val finishedStep = (clock.instant().epochSecond - generator.startTime) / generator.timeStepSeconds
        assertTrue(finishedStep in (step - 1)..(step + 2), "TOTP request left the fixture's checked time interval")
    }
}
