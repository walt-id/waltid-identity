@file:OptIn(
    ExperimentalAbiValidation::class,
    org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class,
)

import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("waltid.mobile.library")
    id("waltid.mobile.sdk.documentation")
    alias(identityLibs.plugins.sqldelight)
}

group = "id.walt.protocols"

waltidMobile {
    androidNamespace.set("id.walt.wallet2.persistence")
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }

    explicitApi()

    abiValidation {
        binariesSource.set(BinariesSource.MAIN_COMPILATION)
    }

    if (enableIosBuild) {
        swiftPMDependencies {
            iosMinimumDeploymentTarget.set("15.4")
            swiftPackage(
                url = url("https://github.com/sqlcipher/SQLCipher.swift.git"),
                version = exact(identityLibs.versions.sqlcipher.swift.get()),
                products = listOf(
                    product(
                        "SQLCipher",
                        platforms = setOf(iOS()),
                        importedClangModules = setOf("SQLCipher"),
                    ),
                ),
            )
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet"))
            api(project(":waltid-libraries:crypto:waltid-crypto2"))
            api(project(":waltid-libraries:crypto:waltid-crypto2-signum"))
            api(project(":waltid-libraries:waltid-did"))
            implementation(identityLibs.sqldelight.runtime)
            implementation(identityLibs.sqldelight.coroutines)
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials-examples"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        if (enableAndroidBuild) {
            androidMain.dependencies {
                implementation(identityLibs.sqldelight.android.driver)
                implementation(identityLibs.sqlcipher.android)
                api(identityLibs.androidx.fragment)
                implementation(identityLibs.androidx.biometric)
                implementation(identityLibs.androidx.lifecycle.runtime)
            }
            named("androidHostTest") {
                dependencies {
                    implementation(identityLibs.sqldelight.sqlite.driver)
                }
            }
        }
        if (enableIosBuild) {
            val appleSources = configurations.create("sqlDelightAppleSources") {
                isCanBeConsumed = false
                isTransitive = false
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
                }
            }
            project.dependencies.add(appleSources.name,
                "app.cash.sqldelight:native-driver-iossimulatorarm64:${identityLibs.versions.sqldelight.get()}")
            val appleDriver = tasks.register<PrepareWalletAppleSqlDriver>("prepareWalletAppleSqlDriver") {
                sourceArchive.fileProvider(appleSources.elements.map { it.single().asFile })
                outputDirectory.set(layout.buildDirectory.dir("generated/walletAppleSqlDriver"))
            }
            iosMain {
                kotlin.srcDir(appleDriver)
                dependencies {
                    implementation(identityLibs.sqliter.driver)
                    implementation(identityLibs.stately.concurrency)
                }
            }
        }
    }
}

sqldelight {
    linkSqlite.set(false)

    databases {
        create("WalletPersistenceDatabase") {
            packageName.set("id.walt.wallet2.persistence.db")
        }
    }
}
