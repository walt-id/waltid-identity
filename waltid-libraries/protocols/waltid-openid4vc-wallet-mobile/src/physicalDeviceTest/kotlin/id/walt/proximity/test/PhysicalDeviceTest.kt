package id.walt.proximity.test

/** Test-only second guard, in addition to the absent-by-default physical source set. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PhysicalDeviceTest
