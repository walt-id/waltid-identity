object BuildConstants {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 30
    const val META_INF_EXCLUDES = "/META-INF/{AL2.0,LGPL2.1}"

    val POWER_ASSERT_FUNCTIONS = listOf(
        "kotlin.assert",
        "kotlin.test.assertEquals",
        "kotlin.test.assertNull",
        "kotlin.test.assertTrue",
        "kotlin.test.assertFalse",
        "kotlin.test.assertContentEquals",
        "kotlin.require",
        "kotlin.check"
    )
}
