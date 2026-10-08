package id.walt.certificate.x509

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class OffsetClock(val offset: Duration) : Clock {

    constructor(nowInTest: Instant) : this(Clock.System.now() - nowInTest)

    override fun now(): Instant = Clock.System.now() - offset
}