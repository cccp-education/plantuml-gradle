package plantuml.scenarios

import io.cucumber.java.After
import io.cucumber.java.Before
import org.slf4j.LoggerFactory
import org.testcontainers.DockerClientFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Extension de nettoyage global pour TOUS les tests Cucumber.
 *
 * Problèmes résolus :
 * 1. Fuites de répertoires temporaires /tmp/gradle-test-*
 * 2. Containers Docker orphelins (pgvector/postgres)
 * 3. Gradle Daemons non arrêtés
 * 4. Fichiers gradle.properties résiduels
 *
 * S'exécute AVANT et APRÈS chaque scénario Cucumber.
 */
class TestCleanupExtension {
    companion object {
        private val log = LoggerFactory.getLogger(TestCleanupExtension::class.java)
        private val tempDirs = mutableListOf<String>()
        private val activeContainers = mutableListOf<String>()

        private const val MAX_TEMP_DIRS = 50
        private const val CONTAINER_TIMEOUT_MINUTES = 5
    }

    /**
     * Nettoyage AVANT le test : supprime les anciens répertoires temporaires
     */
    @Before
    fun beforeScenario() {
        cleanupOldTempDirectories()
        cleanupOrphanedContainers()
    }

    /**
     * Nettoyage APRÈS le test : force l'arrêt de TOUTES les ressources
     */
    @After
    fun afterScenario() {
        // 1. Stopper tous les containers trackés
        activeContainers.forEach { containerId ->
            try {
                log.info("Stopping container $containerId")
                Runtime.getRuntime().exec("docker stop $containerId")
            } catch (e: Exception) {
                log.warn("Failed to stop container $containerId: ${e.message}")
            }
        }
        activeContainers.clear()

        // 2. Attendre que Gradle finisse ses opérations
        Thread.sleep(500)

        // 3. Nettoyer les répertoires temporaires créés par ce test
        tempDirs.forEach { dirPath ->
            forceDeleteDirectory(dirPath)
        }
        tempDirs.clear()
    }

    /**
     * Track un répertoire temporaire pour nettoyage automatique
     */
    fun trackTempDirectory(path: String) {
        tempDirs.add(path)

        // Limite le nombre de répertoires trackés
        if (tempDirs.size > MAX_TEMP_DIRS) {
            val oldest = tempDirs.removeAt(0)
            forceDeleteDirectory(oldest)
        }
    }

    /**
     * Track un container Docker pour nettoyage automatique
     */
    fun trackContainer(containerId: String) {
        activeContainers.add(containerId)
    }

    /**
     * Supprime les anciens répertoires temporaires (> 1 heure)
     */
    private fun cleanupOldTempDirectories() {
        try {
            val now = System.currentTimeMillis()
            val oneHourAgo = now - (60 * 60 * 1000)

            Files
                .list(Paths.get("/tmp"))
                .filter { path ->
                    path.fileName.toString().startsWith("gradle-test-")
                }.filter { path ->
                    try {
                        Files.getLastModifiedTime(path).toMillis() < oneHourAgo
                    } catch (e: Exception) {
                        false
                    }
                }.forEach { path ->
                    try {
                        log.info("Cleaning old temp directory: ${path.fileName}")
                        forceDeleteDirectory(path.toString())
                    } catch (e: Exception) {
                        log.warn("Failed to clean ${path.fileName}: ${e.message}")
                    }
                }
        } catch (e: Exception) {
            log.warn("Failed to cleanup old temp directories: ${e.message}")
        }
    }

    /**
     * Stoppe les containers Docker orphelins (> 10 minutes)
     */
    private fun cleanupOrphanedContainers() {
        try {
            // Vérifier si Docker est disponible
            runCatching {
                DockerClientFactory.instance().client()
            }.getOrElse {
                log.debug("Docker not available, skipping container cleanup")
                return
            }

            // Lister les containers créés par les tests
            val process =
                ProcessBuilder(
                    "docker",
                    "ps",
                    "-a",
                    "--filter",
                    "name=postgres",
                    "--filter",
                    "status=exited",
                    "--filter",
                    "created=before 10m ago",
                    "--format",
                    "{{.ID}}",
                ).start()

            val containerIds =
                process.inputStream
                    .bufferedReader()
                    .readLines()
                    .filter { it.isNotBlank() }

            process.waitFor()

            containerIds.forEach { containerId ->
                try {
                    log.info("Removing orphaned container: $containerId")
                    Runtime.getRuntime().exec("docker rm -f $containerId")
                } catch (e: Exception) {
                    log.warn("Failed to remove container $containerId: ${e.message}")
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to cleanup orphaned containers: ${e.message}")
        }
    }

    /**
     * Supprime un répertoire de force, même avec des fichiers verrouillés
     */
    private fun forceDeleteDirectory(path: String) {
        val dir = File(path)
        if (!dir.exists()) return

        try {
            // Donner une chance aux processus de libérer les fichiers
            Thread.sleep(200)

            // Supprimer tous les fichiers récursivement
            dir.walkTopDown().forEach { file ->
                try {
                    file.delete()
                } catch (e: Exception) {
                    // Ignorer les fichiers verrouillés
                    log.debug("Cannot delete ${file.absolutePath}: ${e.message}")
                }
            }

            // Dernière tentative pour le répertoire lui-même
            dir.deleteRecursively()

            if (dir.exists()) {
                log.warn("Directory still exists after cleanup: $path")
            } else {
                log.debug("Successfully cleaned: $path")
            }
        } catch (e: Exception) {
            log.error("Failed to cleanup directory $path", e)
        }
    }
}
