import app.cash.licensee.LicenseeExtension
import id.walt.gradle.licensee.LicenseePolicies

if (LicenseePolicies.shouldCheck(project)) {
    LicenseePolicies.warmupMavenXmlService()
    pluginManager.apply("app.cash.licensee")
    LicenseePolicies.configure(project, extensions.getByType<LicenseeExtension>())

    tasks.withType<app.cash.licensee.LicenseeTask>().configureEach {
        doFirst {
            LicenseePolicies.warmupMavenXmlService()
        }
    }
}
