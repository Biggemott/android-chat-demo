plugins {
    alias(libs.plugins.ktlint)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
}

ktlint {
    version.set(libs.versions.ktlint)
}

// Keep Detekt's Kotlin compiler on its own classpath and analyze sources without type resolution.
val detektCli = configurations.resolvable("detektCli")
val detektDependencies = configurations.dependencyScope("detektDependencies")
detektCli.configure {
    extendsFrom(detektDependencies.get())
}

dependencies {
    add(detektDependencies.name, libs.detekt.cli)
}

val detekt =
    tasks.register<JavaExec>("detekt") {
        group = "verification"
        description = "Analyzes handwritten Kotlin sources with Detekt."
        mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
        classpath = detektCli.get()
        args("--input", "app/src", "--config", "config/detekt.yml", "--build-upon-default-config")
    }

tasks.register("verifyProject") {
    group = "verification"
    description = "Runs Kotlin style and static analysis, Android Lint, JVM tests with screenshot verification, and debug assembly."
    dependsOn("ktlintCheck", ":app:ktlintCheck", detekt, ":app:lintDebug", ":app:verifyRoborazziDebug", ":app:assembleDebug")
}
