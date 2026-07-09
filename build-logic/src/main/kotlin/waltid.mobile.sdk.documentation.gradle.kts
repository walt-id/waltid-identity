import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

plugins {
    id("org.jetbrains.dokka")
}

dokka {
    dokkaPublications.html {
        moduleName.set(project.name)
        moduleVersion.set(project.version.toString())
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
        failOnWarning.set(true)
        suppressObviousFunctions.set(true)
        includes.from("README.md")
    }

    dokkaSourceSets.configureEach {
        documentedVisibilities.set(setOf(VisibilityModifier.Public))
        reportUndocumented.set(true)
        skipDeprecated.set(false)
        suppressGeneratedFiles.set(true)
    }
}
