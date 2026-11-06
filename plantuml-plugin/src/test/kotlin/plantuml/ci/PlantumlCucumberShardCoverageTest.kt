package plantuml.ci

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guard of the CI BDD partition (CNV-13.6).
 *
 * Every feature is assigned to exactly one file shard; the slow File Edge Cases
 * feature is split by tag into A1/A2 and must cover all its scenarios. A feature
 * added without a shard makes this test red instead of silently inflating one
 * runner (the S-269 lesson: a test not registered is a phantom test).
 */
class PlantumlCucumberShardCoverageTest {
    private val featuresDir = File("src/test/features")

    @Test
    fun `file shards are exhaustive and disjoint`() {
        val all =
            featuresDir
                .listFiles { file -> file.isFile && file.extension == "feature" }
                .orEmpty()
                .map { it.name }
                .toSet()
        assertTrue(all.isNotEmpty(), "no feature found under ${featuresDir.absolutePath}")

        val assigned =
            CucumberShardFeatures.FILE_SHARDS.values
                .flatMap { it.split(",") }
                .map { it.substringAfterLast('/') }

        assertEquals(assigned.size, assigned.toSet().size, "duplicate feature in shards: $assigned")
        assertEquals(
            all,
            (assigned + CucumberShardFeatures.SHARD_A1_FILE).toSet(),
            "a feature is missing from the shard partition",
        )
    }

    @Test
    fun `the tagged File Edge Cases shard covers every scenario`() {
        val lines = File(featuresDir, CucumberShardFeatures.SHARD_A1_FILE).readLines()
        val scenarios = lines.count { it.trimStart().startsWith("Scenario:") }
        val content = lines.count { it.contains(CucumberShardFeatures.SHARD_A1_TAG) }
        val empty = lines.count { it.contains(CucumberShardFeatures.SHARD_A2_TAG) }

        assertTrue(scenarios > 0, "no scenario in ${CucumberShardFeatures.SHARD_A1_FILE}")
        assertTrue(content > 0, "no ${CucumberShardFeatures.SHARD_A1_TAG} scenario")
        assertTrue(empty > 0, "no ${CucumberShardFeatures.SHARD_A2_TAG} scenario")
        assertEquals(scenarios, content + empty, "every scenario must carry exactly one shard tag")
    }
}
