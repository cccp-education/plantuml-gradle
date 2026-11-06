import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.plugin.compatibility.compatibility
import java.time.Duration

// ── buildscript resolutionStrategy ────────────────────────────────────────────────
// Gradle 9.5.1 pinne annotations:13.0 (Kotlin embedded) en strictly.
// koog-agents 0.8.0 → koog-utils/koog-http-client-core/koog-prompt-llm →
// annotations:26.0.2-1. Codebase-plugin exclut koog-agents mais les sous-modules
// koog transitifs contournent l'exclusion. Solution : forcer annotations:26.0.2-1.
buildscript {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains:annotations:26.0.2-1")
            force("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.3.21")
        }
    }
}

plugins {
    `java-library`
    id("education.cccp.build.gradle-plugin") version "0.0.14"
    id("education.cccp.build.publishing") version "0.0.14"
    id("education.cccp.build.lint") version "0.0.14"
    id("education.cccp.build.kover") version "0.0.14"
    alias(libs.plugins.publish)
    alias(libs.plugins.codebase)
}

// Apply the BOM
dependencies {
    implementation(platform("education.cccp:workspace-bom:0.0.121"))
}

group = "education.cccp"
// MEM-CAT-ROLLOUT-3 (D3) — self version derived from the published workspace catalog.
version =
    ws.versions.plantuml.plugin
        .get()

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(libs.node.gradle)

    implementation(libs.bundles.plantuml)
    implementation(libs.bundles.jgit)
    implementation(libs.commons.io)
    implementation(libs.bundles.plantuml.ai)

    // N0 contracts — i18n (cross-borough translation alliance)
    implementation("education.cccp:i18n-contracts:0.0.5")

    // N0 contracts — PlantUML diagram block (EPIC PLT-DIAGRAM-OWNERSHIP US-2).
    // The port + model + validation types live in the contract; this borough
    // provides the implementation (D1/D2/D3).
    implementation("education.cccp:plantuml-contracts:0.0.1")

    // N0 contracts — augmented context (EPIC PLT-CONTEXT CTX-01). The shared
    // CompositeContext / ContextChannel / ChannelBudget seam, fed by the N1
    // codebase; plantuml consumes the contract, never an N2 type.
    implementation("education.cccp:codebase-contracts:0.0.2")

    // Testcontainers for RAG integration (CollectPlantumlIndexTask uses PostgreSQLContainer)
    implementation(libs.testcontainers.pg)

    // Jackson for JSON serialization (using BOM versions)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Coroutines - IMPORTANT for the asynchronous tests
    testImplementation(libs.bundles.coroutines)

    // CNV-7.3 — deps junit test centralisées par education.cccp.build.gradle-plugin (CNV-7.2)
    // kotlin-test-junit5, junit-jupiter, junit-platform-params, assertj-core,
    // junit-platform-launcher, workspace-bom platform injectés automatiquement.
    testImplementation(libs.slf4j)
    testRuntimeOnly(libs.logback)

    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.wiremock)
    testImplementation(libs.testcontainers.pg)
    testImplementation(libs.testcontainers.junit5)

    // Cucumber dependencies (CucumberConventionsPlugin non applicable — cucumberTest custom)
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

// CNV-13.6 — the TestKit builds must run the workspace Gradle (the wrapper),
// never a hardcoded stale version: a `withGradleVersion("9.4.1")` forced CI to
// download and exercise an untested distribution (9.8.2 is the workspace one).
val workspaceGradleVersion =
    layout.projectDirectory
        .file("gradle/wrapper/gradle-wrapper.properties")
        .asFile
        .readText()
        .lineSequence()
        .firstOrNull { it.startsWith("distributionUrl") }
        ?.substringAfter("gradle-")
        ?.substringBefore("-bin.zip")
        ?.substringBefore("-all.zip")
        ?: error("Cannot read the Gradle version from gradle/wrapper/gradle-wrapper.properties")

tasks.withType<Test> {
    // Single source of truth: the wrapper distribution version (CNV-13.6).
    systemProperty("workspace.gradle.version", workspaceGradleVersion)
    useJUnitPlatform {
        // Tests @Tag("real-llm") are excluded by default.
        // To enable them: ./gradlew test -Ptest.tags="real-llm"
        val runRealLlm =
            project
                .findProperty("test.tags")
                ?.toString()
                ?.contains("real-llm") == true

        if (!runRealLlm) {
            excludeTags("real-llm")
        }
    }

    // Single JVM worker to maximize sharing: nested classes reuse WireMock +
    // GradleRunner + sharedProjectDir via PlantumlFunctionalSuite companion.
    maxParallelForks = 1
    forkEvery = 0

    // Strict timeout to prevent blocking (GradleRunner hangs when Ollama does
    // not respond — covered by WireMock in unit tests).
    timeout.set(Duration.ofSeconds(120))

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
        // CI BDD shard runners are dedicated tasks, never unit tests
        excludeTestsMatching("plantuml.ci.shards.**")
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

    // N0 contracts used by the PlantUML translation port (PLT-TERM-PROTECTION FT).
    add(functionalTest.implementationConfigurationName, "education.cccp:i18n-contracts:0.0.5")
    add(functionalTest.implementationConfigurationName, "education.cccp:plantuml-contracts:0.0.1")

    // PLT-OUT-2 — the functional note tests exercise the real N0 syntax validator.
    // The custom functionalTest source set does not inherit the main `implementation`
    // configuration, so the PlantUML engine must be declared explicitly.
    add(functionalTest.implementationConfigurationName, libs.plantuml.engine)

    // Add testcontainers for RAG integration tests
    add(functionalTest.implementationConfigurationName, libs.testcontainers.pg)
    add(functionalTest.implementationConfigurationName, "org.testcontainers:testcontainers")
}

// 3. Task for functional tests
val functionalTestTask =
    tasks.register<Test>("functionalTest") {
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
    java.srcDir("src/test/scenarios") // Steps in scenarios/
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

    // D5-RACE (S-222, mirror graphify S-029/document): the `ws` catalog versions are
    // INJECTED by Gradle. `PlantumlPluginPublicationTest` must never read the sibling
    // repo working tree (racy between parallel sessions, absent in CI where this repo
    // is checked out alone).
    systemProperty(
        "plantuml.publishedCatalog.plantumlVersion",
        ws.versions.plantuml.plugin
            .get(),
    )
    systemProperty(
        "plantuml.publishedCatalog.bomVersion",
        ws.versions.workspace.bom
            .get(),
    )
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
val cucumberTest =
    tasks.register<Test>("cucumberTest") {
        description = "Runs Cucumber BDD tests"
        group = "verification"
        testClassesDirs =
            sourceSets.test
                .get()
                .output.classesDirs
        classpath = configurations.testRuntimeClasspath.get() +
            sourceSets.test.get().output +
            sourceSets.main.get().output +
            sourceSets["functionalTest"].output +
            files(tasks.jar.get().archiveFile) +
            files(tasks.named("pluginUnderTestMetadata"))

        // FIX: Ensure plugin classes are compiled before running tests
        dependsOn(tasks.classes)
        useJUnitPlatform {
            // CORRECTION: Do not filter by tag here, it filters JUnit engines
            // Cucumber scenario filtering is done in the runner via FILTER_TAGS_PROPERTY_NAME
            excludeEngines("junit-jupiter")
        }
        systemProperty("cucumber.junit-platform.naming-strategy", "long")

        // Cucumber tag filter via -Pcucumber.tags="..." (default: exclude @wip, @integration, @realllm)
        val cucumberTags =
            project.findProperty("cucumber.tags")?.toString()
                ?: "not @wip and not @integration and not @realllm and not @skip"
        systemProperty("cucumber.filter.tags", cucumberTags)

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
        // The aggregate runs only the historical runner; the CI shard runners
        // are separate tasks (otherwise every shard would run in the aggregate).
        filter { excludeTestsMatching("plantuml.ci.shards.*") }
        // Ensure main is compiled before
        dependsOn(tasks.classes)

        // OPTIMIZATION: Single JVM worker for Cucumber tests
        maxParallelForks = 1 // ← Single JVM for shared state
        forkEvery = 1 // ← Restart JVM after each test to prevent memory leaks
        jvmArgs("-XX:+UseSerialGC")
        jvmArgs("-XX:MaxMetaspaceSize=256m")
        jvmArgs("-XX:TieredStopAtLevel=1")

        // FIX: Timeout per test to prevent hanging
        timeout.set(Duration.ofMinutes(15))

        // Cleanup after test execution
        doLast {
            println("=== Cucumber Test Cleanup ===")
            println("Cleaning temporary test directories...")

            // Clean old gradle-test-* directories (> 1 hour)
            val tempDir = File(System.getProperty("java.io.tmpdir"))
            val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)

            tempDir
                .listFiles { file ->
                    file.isDirectory &&
                        file.name.startsWith("gradle-test-") &&
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

// ── CI shards (CNV-13.6 — wall-clock budget < 5 min) ────────────────────────
// The aggregate `cucumberTest`/`functionalTest` keep the local shape; CI runs
// the exhaustive, disjoint shards below on parallel machines. The BDD partition
// is guarded by `plantuml.ci.PlantumlCucumberShardCoverageTest`.

data class CucumberTaskSpec(
    val taskName: String,
    val description: String,
    val runnerClass: String,
)

fun registerCucumberShard(spec: CucumberTaskSpec): TaskProvider<Test> =
    tasks.register<Test>(spec.taskName) {
        description = spec.description
        group = "verification"
        testClassesDirs =
            sourceSets.test
                .get()
                .output.classesDirs
        classpath =
            configurations.testRuntimeClasspath.get() +
            sourceSets.test.get().output +
            sourceSets.main.get().output +
            sourceSets["functionalTest"].output +
            files(tasks.jar.get().archiveFile) +
            files(tasks.named("pluginUnderTestMetadata"))
        dependsOn(tasks.classes)
        useJUnitPlatform {
            excludeEngines("junit-jupiter")
        }
        systemProperty("cucumber.junit-platform.naming-strategy", "long")
        systemProperty("org.gradle.daemon", "false")
        // The tag filter lives in the shard runner annotation (FILTER_TAGS_PROPERTY_NAME):
        // A1/A2 carry a shard tag. A task-level `cucumber.filter.tags` would override
        // it (system property precedence) and duplicate the scenarios.
        maxHeapSize = "1g"
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
            exceptionFormat = FULL
        }
        outputs.upToDateWhen { false }
        maxParallelForks = 1
        forkEvery = 0
        jvmArgs("-XX:+UseSerialGC")
        jvmArgs("-XX:MaxMetaspaceSize=256m")
        jvmArgs("-XX:TieredStopAtLevel=1")
        timeout.set(Duration.ofMinutes(15))
        filter { includeTestsMatching(spec.runnerClass) }
    }

val cucumberShardSpecs =
    listOf(
        CucumberTaskSpec(
            "cucumberTestShardA1",
            "BDD shard A1 — File Edge Cases (content scenarios).",
            "plantuml.ci.shards.CucumberShardA1Runner",
        ),
        CucumberTaskSpec(
            "cucumberTestShardA2",
            "BDD shard A2 — File Edge Cases (empty/whitespace scenarios).",
            "plantuml.ci.shards.CucumberShardA2Runner",
        ),
        CucumberTaskSpec(
            "cucumberTestShardB",
            "BDD shard B — diagram types, performance, API key, tiers.",
            "plantuml.ci.shards.CucumberShardBRunner",
        ),
        CucumberTaskSpec(
            "cucumberTestShardC",
            "BDD shard C — i18n configuration, tasks, lifecycle, errors, boundary.",
            "plantuml.ci.shards.CucumberShardCRunner",
        ),
        CucumberTaskSpec(
            "cucumberTestShardD",
            "BDD shard D — round-trip, toolchain, provider, probe, repair, agent, context.",
            "plantuml.ci.shards.CucumberShardDRunner",
        ),
        CucumberTaskSpec(
            "cucumberTestShardE",
            "BDD shard E — block status/correction, notes, term protection, corpus, syntax.",
            "plantuml.ci.shards.CucumberShardERunner",
        ),
        CucumberTaskSpec(
            "cucumberTestShardF",
            "BDD shard F — RAG, LLM providers, errors, configuration, incremental, preserve.",
            "plantuml.ci.shards.CucumberShardFRunner",
        ),
    )

val cucumberShardTasks = cucumberShardSpecs.map { registerCucumberShard(it) }

tasks.register("cucumberShards") {
    description = "Runs every CI BDD shard (exhaustive, disjoint partition)."
    group = "verification"
    dependsOn(cucumberShardTasks)
}

fun registerFunctionalShard(
    taskName: String,
    shardDescription: String,
    configureFilter: Action<TestFilter>,
): TaskProvider<Test> =
    tasks.register<Test>(taskName) {
        description = shardDescription
        group = "verification"
        testClassesDirs = functionalTest.output.classesDirs
        classpath =
            configurations[functionalTest.runtimeClasspathConfigurationName] +
            functionalTest.output
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
        failOnNoDiscoveredTests = false
        maxParallelForks = 1
        filter(configureFilter)
    }

val functionalTestShardA =
    registerFunctionalShard(
        "functionalTestShardA",
        "Functional shard A — plugin suite and knowledge graph.",
    ) {
        includeTestsMatching("plantuml.PlantumlFunctionalSuite*")
        includeTestsMatching("plantuml.KnowledgeGraphDiagramFunctionalTest")
    }

val functionalTestShardB =
    registerFunctionalShard(
        "functionalTestShardB",
        "Functional shard B — context, notes, corpus, providers, quotas.",
    ) {
        includeTestsMatching("plantuml.CollectAugmentedContextFunctionalTest")
        includeTestsMatching("plantuml.GenerateAugmentedContextWiringFunctionalTest")
        includeTestsMatching("plantuml.PlantumlNoteTranslationFunctionalTest")
        includeTestsMatching("plantuml.ValidatePlantumlCorpusFunctionalTest")
        includeTestsMatching("plantuml.PlantumlTermProtectionFunctionalTest")
        includeTestsMatching("plantuml.PreflightPlantumlDescriptionFunctionalTest")
        includeTestsMatching("plantuml.CrossProviderFallbackFunctionalTest")
        includeTestsMatching("plantuml.QuotaResetFunctionalTest")
        includeTestsMatching("plantuml.TieredRotationFunctionalTest")
        includeTestsMatching("plantuml.PlantumlRealLlmFunctionalTest")
        includeTestsMatching("plantuml.SmolLMFineTuneFunctionalTest")
    }

tasks.register("functionalShards") {
    description = "Runs every functionalTest shard (exhaustive, disjoint partition)."
    group = "verification"
    dependsOn(functionalTestShardA, functionalTestShardB)
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

val fineTuningModelDir =
    layout.projectDirectory
        .dir("src/functionalTest/resources/models/SmolLM2-135M-Instruct")

val pythonResourceDir =
    layout.projectDirectory
        .dir("src/functionalTest/resources/python")

val buildFineTuningImage by tasks.registering {
    description = "Builds the Docker image for SmolLM2 fine-tuning tests"
    group = "verification"

    inputs.dir(pythonResourceDir)
    outputs.upToDateWhen {
        val result =
            ProcessBuilder("docker", "image", "inspect", "plantuml-fine-tune:latest")
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .use { it.readText() }
        result.contains("plantuml-fine-tune:latest")
    }

    doLast {
        val dockerfile = pythonResourceDir.asFile.resolve("Dockerfile")
        val pb =
            ProcessBuilder(
                "docker",
                "build",
                "-t",
                "plantuml-fine-tune:latest",
                "-f",
                dockerfile.absolutePath,
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
        downloadScript.writeText(
            """
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
            """.trimIndent(),
        )

        val hfCache = File(temporaryDir, "hf-cache").also { it.mkdirs() }

        val dockerCmd =
            listOf(
                "docker",
                "run",
                "--rm",
                "-v",
                "${modelDir.absolutePath}:/out",
                "-v",
                "${hfCache.absolutePath}:/root/.cache/huggingface",
                "-v",
                "${downloadScript.absolutePath}:/scripts/download.py:ro",
                "-e",
                "HF_HUB_ENABLE_HF_TRANSFER=1",
                "plantuml-fine-tune:latest",
                "python3",
                "/scripts/download.py",
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

// Attach model + image build as dependencies of functionalTest — ONLY when the
// fine-tune suite is requested. The fine-tune tests are tag-gated (excluded by
// default), so downloading a ~270 MB model and building a Docker image on every
// `check`/CI run is pure waste (Loi de l'Économie d'Encre) and breaks CI (no
// Docker/creds). Enabler: `-Ptest.tags="fine-tune"`.
val runFineTuning =
    providers
        .gradleProperty("test.tags")
        .map { it.contains("fine-tune") }
        .orElse(false)

tasks.named("functionalTest") {
    if (runFineTuning.get()) {
        dependsOn(buildFineTuningImage, downloadFineTuningModel)
    }
}
tasks.named(functionalTest.processResourcesTaskName) {
    if (runFineTuning.get()) {
        dependsOn(buildFineTuningImage, downloadFineTuningModel)
    }
}

koverConventions {
    enabled = true
    includedSourceSets = listOf("main")
    htmlDir = "reports/kover/html"
    xmlFile = "reports/kover/xml/report.xml"
    threshold = 75.0
}

gradlePlugin {
    plugins {
        vcsUrl = "https://github.com/cccp-education/plantuml-gradle.git"
        website = "https://cheroliv.com"
        create("plantuml") {
            id =
                libs.plugins.plantuml
                    .get()
                    .pluginId
            implementationClass = "plantuml.PlantumlPlugin"
            displayName = "Plantuml Plugin"
            description = "Gradle plugin for plantuml generation."
            listOf(
                "plantuml",
                "jgit",
                "langchain4j",
                "ollama",
                "kotlin-DSL",
            ).run(tags::set)

            @Suppress("UnstableApiUsage")
            compatibility {
                features {
                    configurationCache = true
                    isolatedProjects = false
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
