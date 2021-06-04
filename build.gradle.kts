import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

application {
    // Define the main class for the application.
    mainClass.set("top.main.MainKt")
}

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin on the JVM.
    kotlin("jvm") version "1.5.0"

    // Apply the application plugin to add support for building a CLI application.
    application

    // Documentation plugin
    id("org.jetbrains.dokka") version "1.4.32"

    // Fat JAR plugin
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

repositories {
    // You can declare any Maven/Ivy/file repository here.
    mavenCentral()
}

dependencies {
    // Align versions of all Kotlin components. We don't need to specify an explicit version for
    // "kotlin-bom" as the Kotlin gradle plugin takes care of it based on the Kotlin version
    // specified in the "plugins" section.
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // --- Dependencies managed by BOM (start) ---
    // We don't need to specify versions for these dependencies, as they come from the "kotlin-bom"
    // dependency. Check a specific release of "kotlin-bom" in the following page to get libraries
    // with version numbers managed by the BOM:
    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-bom
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    // --- Dependencies managed by BOM (end)   ---

    // coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")

    // Kotlin logging with slf4j API and log4j logger
    implementation("io.github.microutils:kotlin-logging:1.7.8")
    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.9.1")
    implementation("org.apache.logging.log4j:log4j-api:2.9.1")
    implementation("org.apache.logging.log4j:log4j-core:2.9.1")

    // Use clikt (command line parser for kotlin) library
    implementation("com.github.ajalt:clikt:2.8.0")

    // use JGraphT library
    implementation("org.jgrapht:jgrapht-core:1.5.0")

    val cplexJarPath: String by project
    println("CPLEX JAR Path: $cplexJarPath")
    implementation(files(cplexJarPath.trim()))

    // Mathjax dokka
    implementation("org.jetbrains.dokka:mathjax-plugin:1.4.10.2")

    // Jackson library to work with JSON/YAML.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.0")
}

tasks {
    register<Delete>("cleanLogs") {
        delete(fileTree("logs") {
            include("*.db", "*.log", "*.lp", "*.yaml")
        })
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_11.toString()
        }
    }

    withType<JavaExec> {
        val cplexLibPath: String by project
        jvmArgs = listOf(
            "-Xms32m",
            "-Xmx4g",
            "-Djava.library.path=${cplexLibPath.trim()}"
        )
    }

    withType<Test> {
        val cplexLibPath: String by project
        jvmArgs = listOf(
            "-Xms32m",
            "-Xmx4g",
            "-Djava.library.path=${cplexLibPath.trim()}"
        )

        testLogging {
            showStandardStreams = true
        }

        addTestListener(object : TestListener {
            override fun beforeTest(p0: TestDescriptor?) = Unit
            override fun beforeSuite(p0: TestDescriptor?) = Unit
            override fun afterTest(desc: TestDescriptor, result: TestResult) = Unit
            override fun afterSuite(desc: TestDescriptor, result: TestResult) {
                // printResults(desc, result)
            }
        })
    }
}

fun printResults(desc: TestDescriptor, result: TestResult) {
    if (desc.parent != null) {
        val output = result.run {
            "Results: $resultType (" +
                    "$testCount tests, " +
                    "$successfulTestCount successes, " +
                    "$failedTestCount failures, " +
                    "$skippedTestCount skipped" +
                    ")"
        }
        val testResultLine = "|  $output  |"
        val repeatLength = testResultLine.length
        val separationLine = "-".repeat(repeatLength)
        println(separationLine)
        println(testResultLine)
        println(separationLine)
    }
}