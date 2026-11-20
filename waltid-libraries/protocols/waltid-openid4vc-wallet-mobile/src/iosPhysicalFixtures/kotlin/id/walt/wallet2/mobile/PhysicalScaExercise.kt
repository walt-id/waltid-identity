package id.walt.wallet2.mobile

import id.walt.wallet2.persistence.keys.IosPlatformKeyProvider

/** Operator-controlled native payment test, excluded from the published framework. */
public class PhysicalScaExercise {
    public suspend fun run(cancelFirst: Boolean): String = exerciseNativeSca(IosPlatformKeyProvider(), cancelFirst)

    @Throws(Exception::class)
    public suspend fun runTimed(operator: PhysicalScaOperator): String =
        exerciseTimedNativeSca(IosPlatformKeyProvider(), operator::beforeStep)
}

/** Explicit operator checkpoint before each physical authentication/control. */
public fun interface PhysicalScaOperator {
    @Throws(Exception::class)
    public suspend fun beforeStep(step: String)
}
