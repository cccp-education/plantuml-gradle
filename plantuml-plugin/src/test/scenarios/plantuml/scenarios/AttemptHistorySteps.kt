package plantuml.scenarios

import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat
import java.io.File

class AttemptHistorySteps(private val world: PlantumlWorld) {

    @Then("attempt history should be tracked with {int} entries")
    fun attemptHistoryShouldHaveEntries(expectedCount: Int) {
        val historyDir = File(world.projectDir, "generated/diagrams")
        assertThat(historyDir).`as`("History directory should exist").exists()

        val historyFiles = historyDir.listFiles { file -> file.extension == "json" }
        assertThat(historyFiles).`as`("Should find JSON history files").isNotNull.isNotEmpty

        val historyFile = historyFiles?.firstOrNull()
        if (historyFile != null) {
            val content = historyFile.readText()
            assertThat(content).contains("\"totalAttempts\" : $expectedCount")
        }
    }

    @Then("the first entry should indicate syntax error")
    fun firstEntryShouldIndicateSyntaxError() {
        val historyDir = File(world.projectDir, "generated/diagrams")
        assertThat(historyDir).`as`("History directory should exist").exists()

        val historyFiles = historyDir.listFiles { file -> file.extension == "json" }
            ?.sortedByDescending { it.lastModified() }

        assertThat(historyFiles).`as`("Should find JSON files").isNotNull.isNotEmpty

        val firstFile = historyFiles?.firstOrNull()
        if (firstFile != null) {
            val content = firstFile.readText()
            val validPattern = Regex("\"(?:is)?Valid\"\\s*:\\s*(true|false)")
            val validMatches = validPattern.findAll(content).map { it.groupValues[1] }.toList()

            assertThat(validMatches).`as`("Should have at least 1 entry").isNotEmpty()
            assertThat(validMatches[0]).`as`("First entry should be invalid").isEqualTo("false")
        }
    }

    @Then("the second entry should indicate success")
    fun secondEntryShouldIndicateSuccess() {
        val historyDir = File(world.projectDir, "generated/diagrams")
        assertThat(historyDir).`as`("History directory should exist").exists()

        val historyFiles = historyDir.listFiles { file -> file.extension == "json" }
            ?.sortedByDescending { it.lastModified() }

        assertThat(historyFiles).`as`("Should find JSON files").isNotNull.isNotEmpty

        val latestFile = historyFiles?.firstOrNull()
        if (latestFile != null) {
            val content = latestFile.readText()

            val validPattern = Regex("\"(?:is)?Valid\"\\s*:\\s*(true|false)")
            val validMatches = validPattern.findAll(content).map { it.groupValues[1] }.toList()

            assertThat(validMatches.size).`as`("Should have at least 2 entries").isGreaterThanOrEqualTo(2)
            assertThat(validMatches[1]).`as`("Second entry should be valid (true)").isEqualTo("true")
        } else {
            throw AssertionError("No JSON files found in history directory")
        }
    }

    @Then("attempt history should be archived with {int} entries")
    fun attemptHistoryShouldBeArchived(expectedCount: Int) {
        val archiveDir = File(world.projectDir, "generated/diagrams")
        assertThat(archiveDir).`as`("Archive directory should exist").exists()

        val archivedFiles = archiveDir.listFiles { file -> file.extension == "json" }
        assertThat(archivedFiles).`as`("Should find JSON archive files").isNotNull.isNotEmpty

        val archiveFile = archivedFiles?.firstOrNull()
        if (archiveFile != null) {
            val content = archiveFile.readText()
            assertThat(content).contains("\"totalAttempts\" : $expectedCount")
        }
    }

    @Then("no diagram should be generated")
    fun noDiagramShouldBeGenerated() {
        val ragDir = File(world.projectDir, "generated/rag")
        if (ragDir.exists()) {
            val diagrams = ragDir.listFiles { file -> file.extension == "puml" }
            assertThat(diagrams).isNullOrEmpty()
        }
    }

    @Then("the first three entries should indicate syntax errors")
    fun firstThreeEntriesShouldIndicateSyntaxErrors() {
        val historyDir = File(world.projectDir, "generated/diagrams")
        assertThat(historyDir).`as`("History directory should exist").exists()

        val historyFiles = historyDir.listFiles { file -> file.extension == "json" }
            ?.sortedByDescending { it.lastModified() }

        assertThat(historyFiles).`as`("Should find at least one JSON file").isNotNull.isNotEmpty

        val latestFile = historyFiles!!.firstOrNull()
        if (latestFile != null) {
            val content = latestFile.readText()

            val validPattern = Regex("\"(?:is)?Valid\"\\s*:\\s*(true|false)")
            val validMatches = validPattern.findAll(content).map { it.groupValues[1] }.toList()

            assertThat(validMatches.size).`as`("Should have at least 3 entries").isGreaterThanOrEqualTo(3)

            for (i in 0 until 3) {
                assertThat(validMatches[i]).`as`("Entry $i should be invalid (false)").isEqualTo("false")
            }
        } else {
            throw AssertionError("No JSON files found in history directory")
        }
    }

    @Then("the fourth entry should indicate success")
    fun fourthEntryShouldIndicateSuccess() {
        val historyDir = File(world.projectDir, "generated/diagrams")
        assertThat(historyDir).`as`("History directory should exist").exists()

        val historyFiles = historyDir.listFiles { file -> file.extension == "json" }
            ?.sortedByDescending { it.lastModified() }

        assertThat(historyFiles).`as`("Should find at least one JSON file").isNotNull.isNotEmpty

        val latestFile = historyFiles!!.firstOrNull()
        if (latestFile != null) {
            val content = latestFile.readText()

            val validPattern = Regex("\"(?:is)?Valid\"\\s*:\\s*(true|false)")
            val validMatches = validPattern.findAll(content).map { it.groupValues[1] }.toList()

            assertThat(validMatches.size).`as`("Should have at least 4 entries").isGreaterThanOrEqualTo(4)
            assertThat(validMatches[3]).`as`("Entry 4 should be valid (true)").isEqualTo("true")
        } else {
            throw AssertionError("No JSON files found in history directory")
        }
    }
}
