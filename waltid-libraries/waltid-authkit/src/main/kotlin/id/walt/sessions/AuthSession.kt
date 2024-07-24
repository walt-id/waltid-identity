package id.walt.sessions

enum class AuthSessionStatus(val value: String) {
    OK("ok"),
    CONTINUE_NEXT_STEP("continue_next_step"),
    FAIL("fail"),
}

data class AuthSession(
    val id: String
)
