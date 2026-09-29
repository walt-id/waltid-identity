package id.walt.wallet2.mobile

import id.walt.wallet2.persistence.keys.IosPlatformKeyProvider

/** Operator-controlled native payment test, excluded from the published framework. */
public class PhysicalScaExercise {
    public suspend fun run(cancelFirst: Boolean): String = exerciseNativeSca(IosPlatformKeyProvider(), cancelFirst)
}
