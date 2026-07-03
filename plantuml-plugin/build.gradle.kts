// ── buildscript resolutionStrategy ────────────────────────────────────────────────
// Gradle 9.5.1 pinne annotations:13.0 (Kotlin embedded) en strictly.
// koog-agents 0.8.0 → koog-utils/koog-http-client-core/koog-prompt-llm →
// annotations:26.0.2-1. Codebase-plugin exclut koog-agents mais les sous-modules
// koog transitifs contournent l'exclusion. Solution : forcer annotations:26.0.2-1.
buildscript {
    configurations.all { resolutionStrategy { force("org.jetbrains:annotations:26.0.2-1") } }
}

import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.plugin.compatibility.compatibility
import java.time.Duration

plugins {
    `java-library`
    id("education.cccp.build.gradle-plugin") version "0.0.1"
    id("education.cccp.build.publishing") version "0.0.1"
    alias(libs.plugins.publish)
    alias(libs.plugins.kover)
    alias(libs.plugins.codebase)
}

// Apply the BOM
dependencies {
    implementation(platform("education.cccp:workspace-bom:0.0.1"))
}

group = "education.cccp"
version = libs.plugins.plantuml.get().version

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(libs.bundles.asciidoctor)
    implementation(libs.node.gradle)

    api(libs.bundles.plantuml)
    api(libs.bundles.jgit)
    api(libs.commons.io)
    api(libs.bundles.plantuml.ai)

    // Testcontainers for RAG integration tests
    api(libs.testcontainers.pg)

    // Jackson for JSON serialization (using BOM versions)
    api("com.fasterxml.jackson.module:jackson-module-kotlin")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Coroutines - IMPORTANT for the asynchronous tests
    testImplementation(libs.bundles.coroutines)

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.slf4j)
    testRuntimeOnly(libs.logback)

    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.junit.platform.params)
    testImplementation(libs.wiremock)
    testImplementation(libs.testcontainers.pg)
    testImplementation(libs.testcontainers.junit5)

    // Cucumber dependencies
    testImplementation(libs.bundles.cucumber)
}

configurations.all {
    resolutionStrategy {
        // Force Groovy version used by Gradle
        force(libs.groovy)
        force(libs.groovy.nio)
    }
}

// Exclude conflicting Groovy dependencies only for certain configurations
configurations.configureEach {
    // Do not exclude for testImplementation as it may break tests
    if (name != "testImplementation" && name != "testRuntimeOnly") {
        exclude(group = "org.codehaus.groovy")
    }
}


tasks.withType<Test> {
    useJUnitPlatform {
        // Tests @Tag("real-llm") are excluded by default.
        // To enable them: ./gradlew test -Ptest.tags="real-llm"
        val runRealLlm = project.findProperty("test.tags")
            ?.toString()
            ?.contains("real-llm") == true

        if (!runRealLlm) {
            excludeTags("real-llm")
        }

        // Global timeout per test — prevents GradleRunner from hanging
        // if Ollama doesn't respond (covered by WireMock in unit tests)
        timeout.set(Duration.ofSeconds(30))

        // Parallel execution of test classes (nested classes share their state
        // via companion object, so parallelization is at class level)
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    }

    // OPTIMIZATION: Single JVM worker to maximize sharing
    // Nested classes share WireMock + GradleRunner + sharedProjectDir
    // via the PlantumlFunctionalSuite companion object
    maxParallelForks = 1 // ← Single JVM: maximum reuse
    forkEvery = 0 // ← Never restart the worker (0 = unlimited)

    // Strict timeout to prevent blocking
    timeout.set(Duration.ofSeconds(60))

    // Reuse outputs to speed up executions
    outputs.cacheIf { true }

    // JVM options optimized for tests
    jvmArgs("-XX:+UseSerialGC") // Faster GC for short tests
    jvmArgs("-XX:MaxMetaspaceSize=256m") // Stricter memory limit
    jvmArgs("-XX:TieredStopAtLevel=1") // Disable JIT for fast startup
}

tasks.named<Test>("test") {
    filter {
        // Exclude classes in 'plantuml.scenarios' package (Cucumber tests)
        excludeTestsMatching("plantuml.scenarios.**")
        // Also exclude functionalTest classes
        excludeTestsMatching("plantuml.PlantUmlPluginFunctionalTests")
    }
}


// 1. Create the functionalTest SourceSet
val functionalTest: SourceSet by sourceSets.creating {
    java.srcDirs("src/functionalTest/kotlin")
    resources.srcDirs("src/functionalTest/resources")
}

// 2. Add GradleTestKit to functionalTest (WITHOUT inheriting from testImplementation)
dependencies {
    add(functionalTest.implementationConfigurationName, gradleTestKit())
    add(functionalTest.implementationConfigurationName, kotlin("stdlib-jdk8"))
    add(functionalTest.implementationConfigurationName, kotlin("test"))
    add(functionalTest.implementationConfigurationName, kotlin("test-junit5"))

    // Add required dependencies explicitly (using BOM versions)
    add(functionalTest.implementationConfigurationName, "org.slf4j:slf4j-api")
    add(functionalTest.runtimeOnlyConfigurationName, "ch.qos.logback:logback-classic")
    add(functionalTest.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher")

    // CORRECTION: Add AssertJ for assertions
    add(functionalTest.implementationConfigurationName, libs.assertj.core)

    // Add Mockito if necessary
    add(functionalTest.implementationConfigurationName, libs.mockito.kotlin)
    add(functionalTest.implementationConfigurationName, libs.mockito.junit.jupiter)
    add(functionalTest.implementationConfigurationName, libs.wiremock)
    add(functionalTest.implementationConfigurationName, libs.junit.platform.params)

    libs.bundles.coroutines.get().forEach { dep ->
        add(functionalTest.implementationConfigurationName, dep)
    }

    // CORRECTION: Add LangChain4j to access ChatModel classes
    add(functionalTest.implementationConfigurationName, libs.langchain4j)
    add(functionalTest.implementationConfigurationName, libs.langchain4j.ollama)

    // CORRECTION: Add dependency to main source code to access plugin classes
    add(functionalTest.implementationConfigurationName, sourceSets.main.get().output)

    // Add testcontainers for RAG integration tests
    add(functionalTest.implementationConfigurationName, libs.testcontainers.pg)
    add(functionalTest.implementationConfigurationName, "org.testcontainers:testcontainers")
}

// 3. Task for functional tests
val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs functional tests."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = configurations[functionalTest.runtimeClasspathConfigurationName] + functionalTest.output
    useJUnitPlatform {
        val runRealLlm = project.findProperty("test.tags")?.toString()?.contains("real-llm") == true
        val runFineTune = project.findProperty("test.tags")?.toString()?.contains("fine-tune") == true
        if (!runRealLlm) {
            excludeTags("real-llm")
        }
        if (!runFineTune) {
            excludeTags("fine-tune")
        }
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    failOnNoDiscoveredTests = false

    // Timeout for functional tests - WireMock mocks prevent real network calls
    timeout.set(Duration.ofMinutes(5))

    // Forward gradle project property to system property for @EnabledIfSystemProperty
    project.findProperty("test.tags")?.toString()?.let { tags ->
        systemProperty("test.tags", tags)
    }

    // Add system properties for permission tests
    systemProperty("test.timeout.multiplier", "2")

    // OPTIMIZATION: Parallel tests to reduce execution time
    // Tests are isolated with @TempDir, no shared state between classes
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    forkEvery = 0
    jvmArgs("-XX:+UseSerialGC")
    jvmArgs("-XX:MaxMetaspaceSize=256m")
    jvmArgs("-XX:TieredStopAtLevel=1")
}

// CORRECTION: Handle resource duplications for functionalTest
tasks.named<ProcessResources>(functionalTest.processResourcesTaskName) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// 4. Configure source sets for Cucumber (standard test)
sourceSets.test {
    resources.srcDir("src/test/features")
    java.srcDir("src/test/scenarios")  // Steps in scenarios/
}


// 5. Make testImplementation inherit from functionalTest (not the other way around!)
configurations.named("testImplementation").configure {
    extendsFrom(configurations.named(functionalTest.implementationConfigurationName).get())
}

configurations.named("testRuntimeOnly").configure {
    extendsFrom(configurations.named(functionalTest.runtimeOnlyConfigurationName).get())
}

// 6. Add compiled functionalTest classes to test classpath
dependencies { testImplementation(functionalTest.output) }

// Specific configuration for plugin tests
tasks.named<Test>("test") {
    // Ajouter le jar du plugin au classpath des tests
    classpath += files(tasks.named("jar"))

    // Add required system properties
    systemProperty("gradle.plugin.repository", project.rootDir.resolve("build/libs").absolutePath)
}

configurations {
    // Exclude logback-classic from test classpath
    named("testRuntimeClasspath") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
    }
    named("testImplementation") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
    }
    // Exclude logback-classic from functionalTest classpath
    named(functionalTest.runtimeClasspathConfigurationName) {
        exclude(group = "ch.qos.logback", module = "logback-classic")
    }
}

// 7. Task dedicated to Cucumber tests
val cucumberTest = tasks.register<Test>("cucumberTest") {
    description = "Runs Cucumber BDD tests"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = configurations.testRuntimeClasspath.get() +
            sourceSets.test.get().output +
            sourceSets.main.get().output +
            sourceSets["functionalTest"].output +
            files(tasks.jar.get().archiveFile)

    // FIX: Ensure plugin classes are compiled before running tests
    dependsOn(tasks.classes)
    useJUnitPlatform {
        // CORRECTION: Do not filter by tag here, it filters JUnit engines
        // Cucumber scenario filtering is done in the runner via FILTER_TAGS_PROPERTY_NAME
        excludeEngines("junit-jupiter")
    }
    systemProperty("cucumber.junit-platform.naming-strategy", "long")

    // FIX: Disable Gradle daemon for tests to avoid startup overhead and memory leaks
    systemProperty("org.gradle.daemon", "false")

    // Memory leak prevention: limit heap size
    maxHeapSize = "1g"

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = FULL
    }
    outputs.upToDateWhen { false }
    // Ensure main is compiled before
    dependsOn(tasks.classes)

    // OPTIMIZATION: Single JVM worker for Cucumber tests
    maxParallelForks = 1 // ← Single JVM for shared state
    forkEvery = 1 // ← Restart JVM after each test to prevent memory leaks
    jvmArgs("-XX:+UseSerialGC")
    jvmArgs("-XX:MaxMetaspaceSize=256m")
    jvmArgs("-XX:TieredStopAtLevel=1")

    // FIX: Timeout per test to prevent hanging
    timeout.set(Duration.ofMinutes(5))

    // Cleanup after test execution
    doLast {
        println("=== Cucumber Test Cleanup ===")
        println("Cleaning temporary test directories...")

        // Clean old gradle-test-* directories (> 1 hour)
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)

        tempDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("gradle-test-") &&
            file.lastModified() < oneHourAgo
        }?.forEach { oldDir ->
            try {
                if (oldDir.deleteRecursively()) {
                    println("  ✓ Cleaned: ${oldDir.name}")
                } else {
                    println("  ✗ Failed to clean: ${oldDir.name}")
                }
            } catch (e: Exception) {
                println("  ✗ Error cleaning ${oldDir.name}: ${e.message}")
            }
        }

        println("=== Cleanup complete ===")
    }
}

tasks.withType<Test>().configureEach {
    // Allows hiding the warning about dynamic agent loading
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

tasks.check {
    dependsOn(functionalTestTask)
    dependsOn(cucumberTest)
}

// ------------------------------------------------------------------ //
//  Fine-tuning model download (pre-requisite for real fine-tune test) //
// ------------------------------------------------------------------ //

val fineTuningModelDir = layout.projectDirectory
    .dir("src/functionalTest/resources/models/SmolLM2-135M-Instruct")

val pythonResourceDir = layout.projectDirectory
    .dir("src/functionalTest/resources/python")

val buildFineTuningImage by tasks.registering {
    description = "Builds the Docker image for SmolLM2 fine-tuning tests"
    group = "verification"

    inputs.dir(pythonResourceDir)
    outputs.upToDateWhen {
        val result = ProcessBuilder("docker", "image", "inspect", "plantuml-fine-tune:latest")
            .redirectErrorStream(true)
            .start()
            .inputStream
            .bufferedReader()
            .use { it.readText() }
        result.contains("plantuml-fine-tune:latest")
    }

    doLast {
        val dockerfile = pythonResourceDir.asFile.resolve("Dockerfile")
        val pb = ProcessBuilder(
            "docker", "build",
            "-t", "plantuml-fine-tune:latest",
            "-f", dockerfile.absolutePath,
            dockerfile.parent,
        ).inheritIO()
        val exit = pb.start().waitFor()
        if (exit != 0) {
            throw GradleException("Docker build failed with exit code $exit")
        }
    }
}

val downloadFineTuningModel by tasks.registering {
    description = "Downloads HuggingFaceTB/SmolLM2-135M-Instruct for fine-tuning tests"
    group = "verification"

    outputs.dir(fineTuningModelDir)

    doLast {
        val modelDir = fineTuningModelDir.asFile
        val safetensors = File(modelDir, "model.safetensors")
        if (safetensors.exists()) {
            logger.lifecycle("Model already present at ${modelDir.absolutePath} — skipping download")
            return@doLast
        }
        logger.lifecycle("Downloading HuggingFaceTB/SmolLM2-135M-Instruct with plantuml-fine-tune:latest ...")
        modelDir.mkdirs()

        val downloadScript = File(temporaryDir, "download.py")
        downloadScript.writeText("""
import sys, time
from huggingface_hub import snapshot_download
for attempt in range(1, 4):
    try:
        snapshot_download(
            "HuggingFaceTB/SmolLM2-135M-Instruct",
            local_dir="/out",
            ignore_patterns=["onnx/**","runs/**","*.bin","*.msgpack",
                             "all_results.json","eval_results.json",
                             "train_results.json","trainer_state.json","training_args.bin"],
            max_workers=2,
        )
        print("Model downloaded to /out")
        sys.exit(0)
    except Exception as e:
        print(f"Attempt {attempt}/3 failed: {e}")
        if attempt == 3:
            sys.exit(1)
        time.sleep(5)
        """.trimIndent())

        val hfCache = File(temporaryDir, "hf-cache").also { it.mkdirs() }

        val dockerCmd = listOf(
            "docker", "run", "--rm",
            "-v", "${modelDir.absolutePath}:/out",
            "-v", "${hfCache.absolutePath}:/root/.cache/huggingface",
            "-v", "${downloadScript.absolutePath}:/scripts/download.py:ro",
            "-e", "HF_HUB_ENABLE_HF_TRANSFER=1",
            "plantuml-fine-tune:latest",
            "python3", "/scripts/download.py",
        )

        val pb = ProcessBuilder(dockerCmd).inheritIO()
        val exit = pb.start().waitFor()
        if (exit != 0) {
            throw GradleException("Model download failed with exit code $exit")
        }
        check(safetensors.exists()) {
            "Download failed — model.safetensors missing in ${modelDir.absolutePath}"
        }
    }
}

// Attach model + image build as dependencies of functionalTest
tasks.named("functionalTest") {
    dependsOn(buildFineTuningImage, downloadFineTuningModel)
}
tasks.named(functionalTest.processResourcesTaskName) {
    dependsOn(buildFineTuningImage, downloadFineTuningModel)
}


kover {
    currentProject {
        sources {
            // Include main + functionalTest in coverage
            // By default, Kover already includes 'main' and excludes 'test'
            // We explicitly add functionalTest
            includedSourceSets.addAll("main", "functionalTest")
        }
    }
    reports {
        total {
            html {
                onCheck.set(true)
                htmlDir.set(layout.buildDirectory.dir("reports/kover/html"))
            }
            xml {
                onCheck.set(true)
                xmlFile.set(layout.buildDirectory.file("reports/kover/xml/report.xml"))
            }
        }
    }
}

// Kover verification - fail build if coverage < 75%
tasks.register("koverThresholdCheck") {
    doLast {
        val reportFile = layout.buildDirectory.file("reports/kover/xml/report.xml").get().asFile
        if (!reportFile.exists()) {
            throw GradleException("Kover report not found. Run 'koverXmlReport' first.")
        }
        val xml = reportFile.readText()
        // Aggregate all INSTRUCTION counters from the report
        val coverageRegex = Regex("""<counter type="INSTRUCTION" missed="(\d+)" covered="(\d+)"/>""")
        val matches = coverageRegex.findAll(xml)
        var totalMissed = 0L
        var totalCovered = 0L
        for (match in matches) {
            totalMissed += match.groupValues[1].toLong()
            totalCovered += match.groupValues[2].toLong()
        }
        val total = totalMissed + totalCovered
        val coverage = if (total > 0) (totalCovered.toDouble() / total) * 100 else 0.0
        println(
            "Instruction coverage: ${
                String.format(
                    "%.2f",
                    coverage
                )
            }% (missed=$totalMissed, covered=$totalCovered)"
        )
        if (coverage < 75.0) {
            throw GradleException("Coverage ${String.format("%.2f", coverage)}% is below threshold 75%")
        }
    }
}

tasks.check { dependsOn("koverThresholdCheck") }

gradlePlugin {
    plugins {
        vcsUrl = "https://github.com/cheroliv/plantuml-gradle.git"
        website = "https://cheroliv.com"
        create("plantuml") {
            id = libs.plugins.plantuml.get().pluginId
            implementationClass = "plantuml.PlantumlPlugin"
            displayName = "Plantuml Plugin"
            description = "Gradle plugin for plantuml generation."
            listOf(
                "plantuml",
                "jgit",
                "langchain4j",
                "ollama",
                "kotlin-DSL"
            ).run(tags::set)

            @Suppress("UnstableApiUsage")
            compatibility {
                features {
                    // asciidoctorRevealJs runs OUT_OF_PROCESS via JRuby — not compatible
                    // with Configuration Cache. Will be revisited when asciidoctor-gradle
                    // stabilises beyond 5.0.0-alpha.1.
                    configurationCache = false
                }
            }
        }
    }
    testSourceSets(functionalTest)
}

publishingConventions {
    publicationType = "PLUGIN"
}

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                name.set(gradlePlugin.plugins.getByName("plantuml").displayName)
                description.set(gradlePlugin.plugins.getByName("plantuml").description)
            }
        }
    }
    repositories {
        mavenCentral()
    }
}
