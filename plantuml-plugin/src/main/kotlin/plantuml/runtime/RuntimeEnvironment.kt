package plantuml.runtime

/**
 * Single seam over the process-level execution environment.
 *
 * Production code must consult this seam instead of reading `System.getProperty`
 * or `System.getenv` directly: the test-only concerns (test mode, real-LLM
 * override, disk-full simulation, project directory, provider timeout) are
 * gathered here and can be substituted by a fake in unit tests.
 *
 * The default [system] implementation reads the same system properties and
 * environment variables as before, so the Gradle `-D`/`-P` contract used by
 * functional tests is preserved.
 */
interface RuntimeEnvironment {

    /** `plantuml.test.mode=true` or `TEST_ENV=true`. */
    val isTestMode: Boolean

    /** `plantuml.real.test=true` — bypasses the chat-model null guard. */
    val isRealLlmTest: Boolean

    /** `plantuml.test.disk.full=true` — simulated disk-full failure. */
    val isDiskFullSimulated: Boolean

    /** `plugin.project.dir`, falling back to `user.dir`, then `"."`. */
    val projectDir: String

    /** Provider timeout: 5s under `TEST_ENV=true`, 60s otherwise. */
    val providerTimeoutSeconds: Long

    companion object {
        fun system(
            property: (String) -> String? = System::getProperty,
            environment: (String) -> String? = System::getenv,
        ): RuntimeEnvironment = SystemRuntimeEnvironment(property, environment)
    }
}

private const val TEST_MODE_PROPERTY = "plantuml.test.mode"
private const val REAL_LLM_TEST_PROPERTY = "plantuml.real.test"
private const val DISK_FULL_PROPERTY = "plantuml.test.disk.full"
private const val PROJECT_DIR_PROPERTY = "plugin.project.dir"
private const val USER_DIR_PROPERTY = "user.dir"
private const val TEST_ENV_VARIABLE = "TEST_ENV"

private class SystemRuntimeEnvironment(
    private val property: (String) -> String?,
    private val environment: (String) -> String?,
) : RuntimeEnvironment {

    override val isTestMode: Boolean
        get() = property(TEST_MODE_PROPERTY) == "true" || environment(TEST_ENV_VARIABLE) == "true"

    override val isRealLlmTest: Boolean
        get() = property(REAL_LLM_TEST_PROPERTY) == "true"

    override val isDiskFullSimulated: Boolean
        get() = property(DISK_FULL_PROPERTY) == "true"

    override val projectDir: String
        get() = property(PROJECT_DIR_PROPERTY) ?: property(USER_DIR_PROPERTY) ?: "."

    override val providerTimeoutSeconds: Long
        get() = if (environment(TEST_ENV_VARIABLE) == "true") 5L else 60L
}
