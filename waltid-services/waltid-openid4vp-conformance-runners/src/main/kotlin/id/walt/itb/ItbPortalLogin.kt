package id.walt.itb

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.TimeoutError
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.WaitUntilState
import java.util.regex.Pattern

/** Cold portal assets can outlive the normal DOM timeout. Readiness is the login form, not every page resource. */
internal object ItbPortalLogin {
    fun login(
        page: Page, testBed: String, username: String, password: String,
        startupTimeoutMillis: Double = ItbPortalBridge.APPLICATION_READY_TIMEOUT_MILLIS,
    ) {
        var step = ItbPortalStepTimeout.Step.LOGIN_NAVIGATION
        try {
            page.navigate("$testBed/app#/login", Page.NavigateOptions().setWaitUntil(WaitUntilState.COMMIT))
            step = ItbPortalStepTimeout.Step.LOGIN_FORM
            page.getByRole(AriaRole.TEXTBOX, Page.GetByRoleOptions().setName("Username:"))
                .fill(username, Locator.FillOptions().setTimeout(startupTimeoutMillis))
            page.getByRole(AriaRole.TEXTBOX, Page.GetByRoleOptions().setName("Password:")).fill(password)
            step = ItbPortalStepTimeout.Step.LOGIN_SUBMIT
            page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(Pattern.compile("Log in"))).click()
            step = ItbPortalStepTimeout.Step.LOGIN_HOME
            page.waitForURL("$testBed/app#/home", Page.WaitForURLOptions().setWaitUntil(WaitUntilState.COMMIT))
        } catch (_: TimeoutError) {
            throw ItbPortalStepTimeout(step)
        }
    }
}
