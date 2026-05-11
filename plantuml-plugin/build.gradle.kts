import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.plugin.compatibility.compatibility
import java.time.Duration

plugins {
    `java-library`
    signing
    `maven-publish`
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.publish)
    alias(libs.plugins.kover)
}

group = "com.cheroliv"
version = libs.plugins.plantuml.get().version
kotlin.jvmToolchain(JavaVersion.VERSION_24.ordinal)

repositories {
    mavenCentral()
    gradlePluginPortal()
}

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

    // Jackson for JSON serialization
    api(libs.jackson.module.kotlin)
    api(libs.jackson.dataformat.yaml)
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")

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
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
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

    // Add required dependencies explicitly
    add(functionalTest.implementationConfigurationName, "org.slf4j:slf4j-api:2.0.17")
    add(functionalTest.runtimeOnlyConfigurationName, "ch.qos.logback:logback-classic:1.5.26")
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
}

// 3. Task for functional tests
val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs functional tests."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = configurations[functionalTest.runtimeClasspathConfigurationName] + functionalTest.output
    useJUnitPlatform()
//    useJUnitPlatform { includeTags("real-llm") }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    failOnNoDiscoveredTests = false

    // Timeout for functional tests - WireMock mocks prevent real network calls
    timeout.set(Duration.ofMinutes(5))

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

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        withType<MavenPublication> {
            if (name == "pluginMaven") {
                pom {
                    name.set(gradlePlugin.plugins.getByName("plantuml").displayName)
                    description.set(gradlePlugin.plugins.getByName("plantuml").description)
                    url.set(gradlePlugin.website.get())
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("cheroliv")
                            name.set("cheroliv")
                            email.set("cheroliv.developer@gmail.com")
                        }
                    }
                    scm {
                        connection.set(gradlePlugin.vcsUrl.get())
                        developerConnection.set(gradlePlugin.vcsUrl.get())
                        url.set(gradlePlugin.vcsUrl.get())
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = (if (version.toString().endsWith("-SNAPSHOT"))
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            else uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"))
            credentials {
                username = project.findProperty("ossrhUsername") as? String
                password = project.findProperty("ossrhPassword") as? String
            }
        }
        mavenCentral()
    }
}

signing {
    if (System.getenv("CI") != "true" && !version.toString().endsWith("-SNAPSHOT")) {
        sign(publishing.publications)
    }
    useGpgCmd()
}
