package id.walt.authkit.sessions

import kotlin.uuid.Uuid

enum class AuthSessionStatus(val value: String) {
    OK("ok"),
    CONTINUE_NEXT_STEP("continue_next_step"),
    FAIL("fail"),
}

@OptIn(ExperimentalStdlibApi::class)
data class AuthSession(
    val id: Uuid
)
