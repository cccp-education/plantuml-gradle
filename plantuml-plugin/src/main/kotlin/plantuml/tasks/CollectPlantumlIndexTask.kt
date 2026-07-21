package plantuml.tasks

import dev.langchain4j.data.document.Document.document
import dev.langchain4j.data.document.DocumentSplitter
import dev.langchain4j.data.document.Metadata.metadata
import dev.langchain4j.data.document.splitter.DocumentSplitters
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.testcontainers.containers.PostgreSQLContainer
import plantuml.PlantumlConfig
import plantuml.PlantumlManager
import plantuml.PlantumlMessages
import plantuml.incremental.IncrementalProcessor
import java.io.File

/**
 * RAG (Retrieval-Augmented Generation) execution mode.
 *
 * Determines how vector embeddings are stored during RAG indexing:
 * - [SIMULATION]: Generates embeddings without persisting (for testing/demo)
 * - [DATABASE]: Stores embeddings in PostgreSQL with pgvector extension
 * - [TESTCONTAINERS]: Uses ephemeral PostgreSQL container for integration tests
 */
enum class RagMode { SIMULATION, DATABASE, TESTCONTAINERS }

/**
 * Gradle task: `collectPlantumlIndex`
 *
 * Rebuilds the RAG (Retrieval-Augmented Generation) index from collected PlantUML diagrams.
 *
 * **Workflow**:
 * 1. Loads PlantUML diagrams from RAG directory (`.puml` files)
 * 2. Loads attempt history files (`.json` from LLM corrections)
 * 3. Splits documents into segments using LangChain4j DocumentSplitter
 * 4. Generates 384-dimensional embeddings using All-MiniLM-L6-v2 model
 * 5. Stores embeddings in vector database based on RAG mode
 *
 * **RAG Modes** (priority: CLI > env > gradle prop > config):
 * - `simulation` — Generates embeddings without storage (default)
 * - `database` — PostgreSQL with pgvector extension
 * - `testcontainers` — Ephemeral PostgreSQL container for tests
 *
 * **Configuration**:
 * ```bash
 * # CLI parameter (highest priority)
 * ./gradlew collectPlantumlIndex -Prag.mode=database
 *
 * # Environment variable
 * RAG_MODE=database ./gradlew collectPlantumlIndex
 *
 * # Gradle property
 * ./gradlew collectPlantumlIndex -Prag.mode=database
 * ```
 *
 * **Usage**:
 * ```bash
 * ./gradlew collectPlantumlIndex
 * ```
 */
@DisableCachingByDefault(because = "RAG indexing processes all files and results depend on current state")
abstract class CollectPlantumlIndexTask : DefaultTask() {

    private val lang: String = PlantumlManager.resolveLanguage(project)

    init {
        group = PlantumlMessages.get("task.collect.group", lang)
        description = PlantumlMessages.get("task.collect.description", lang)
    }

    /**
     * Main task action: rebuilds RAG index from PlantUML diagrams and training data.
     *
     * Determines RAG mode, loads diagrams and attempt history files, initializes
     * embedding model and document splitter, then executes indexing based on mode.
     *
     * @throws RuntimeException if RAG directory is inaccessible or not a directory
     */
    @TaskAction
    fun reindexRag() {
        logger.lifecycle(PlantumlMessages.get("collect.rebuilding", lang))

        // Check for test mode port conflict simulation FIRST, before determining RAG mode
        val simulatePortConflict = System.getProperty("plantuml.test.simulate.port.conflict") == "true" ||
            project.properties["plantuml.test.simulate.port.conflict"]?.toString() == "true"
        if (simulatePortConflict) {
            val message = PlantumlMessages.get("collect.port_conflict", lang)
            logger.error(message)
            throw RuntimeException(message)
        }

        // Extract CLI parameters from project properties and strip "plantuml." prefix
        val cliParams = project.properties
            .filterKeys { it.startsWith("plantuml.") }
            .mapKeys { it.key.removePrefix("plantuml.") }
            .mapValues { it.value }

        // Load configuration with CLI overrides
        val config = PlantumlManager.Configuration.load(project, cliParams)

        // Determine RAG mode from multiple sources (priority: CLI > env > gradle prop > config)
        val ragMode = determineRagMode(cliParams, config)

        // Load valid PlantUML diagrams from the RAG collection directory
        val ragDir = File(config.output.rag)
        if (!ragDir.exists()) {
            logger.lifecycle(PlantumlMessages.format("collect.no_rag_dir", lang, ragDir.absolutePath))
            ragDir.mkdirs()
            logger.lifecycle(PlantumlMessages.get("collect.created_rag_dir", lang))
            return
        }

        // Check if the path is actually a directory
        if (!ragDir.isDirectory) {
            val errorMsg = PlantumlMessages.format("collect.not_dir", lang, ragDir.absolutePath)
            logger.error(errorMsg)
            throw RuntimeException(errorMsg)
        }

        // Check if we can read the directory
        if (!ragDir.canRead()) {
            val errorMsg = PlantumlMessages.format("collect.permission_denied_dir", lang, ragDir.absolutePath)
            logger.error(errorMsg)
            throw RuntimeException(errorMsg)
        }

        val diagramFiles = try {
            ragDir.listFiles { file ->
                file.extension == "puml"
            } ?: emptyArray()
        } catch (e: SecurityException) {
            val errorMsg = PlantumlMessages.format(
                "collect.permission_denied_files", lang, ragDir.absolutePath, e.message ?: ""
            )
            logger.error(errorMsg)
            throw RuntimeException(errorMsg, e)
        } catch (e: Exception) {
            val errorMsg = PlantumlMessages.format(
                "collect.error_access_dir", lang, ragDir.absolutePath, e.message ?: ""
            )
            logger.error(errorMsg)
            throw RuntimeException(errorMsg, e)
        }

        // Also load attempt history files for training data
        val trainingDirPath = if (System.getProperty("plantuml.test.mode") == "true") {
            config.output.diagrams
        } else {
            config.output.rag
        }
        val trainingDir = File(trainingDirPath)
        val historyFiles = if (trainingDir.exists()) {
            try {
                trainingDir.listFiles { file ->
                    file.extension == "json" && file.name.startsWith("attempt-history")
                } ?: emptyArray()
            } catch (e: SecurityException) {
                val errorMsg = PlantumlMessages.format(
                    "collect.permission_denied_training", lang, trainingDir.absolutePath, e.message ?: ""
                )
                logger.error(errorMsg)
                throw RuntimeException(errorMsg, e)
            }
        } else {
            emptyArray()
        }

        val promptsDir = project.file(config.input.prompts)
        val checksumsDir = File(project.buildDir, config.incremental.checksumsDir.removePrefix("build/"))
        val incrementalProcessor = IncrementalProcessor(checksumsDir)

        val (toReindex, skipped) = diagramFiles.partition { pumlFile ->
            incrementalProcessor.shouldReindexPuml(pumlFile, promptsDir)
        }
        skipped.forEach { pumlFile ->
            logger.lifecycle(PlantumlMessages.format("collect.skipped_unchanged", lang, pumlFile.name))
        }
        val filteredDiagramFiles = toReindex.toTypedArray()

        if (filteredDiagramFiles.isEmpty() && historyFiles.isEmpty()) {
            logger.lifecycle(PlantumlMessages.get("collect.no_data", lang))
            return
        }

        logger.lifecycle(PlantumlMessages.format("collect.found_data", lang, filteredDiagramFiles.size, historyFiles.size))

        val embeddingModel: EmbeddingModel = if (System.getProperty("plantuml.test.rag.mode") != null) {
            val stubClass = System.getProperty("plantuml.test.embedding.model.class")
            if (stubClass != null) {
                Class.forName(stubClass).getDeclaredConstructor().newInstance() as EmbeddingModel
            } else {
                AllMiniLmL6V2EmbeddingModel()
            }
        } else {
            AllMiniLmL6V2EmbeddingModel()
        }

        // Initialize document splitter
        val documentSplitter: DocumentSplitter = DocumentSplitters.recursive(300, 0)

        // Execute based on RAG mode
        when (ragMode) {
            RagMode.DATABASE -> {
                executeDatabaseMode(filteredDiagramFiles, historyFiles, config, embeddingModel, documentSplitter)
            }
            RagMode.TESTCONTAINERS -> {
                executeTestcontainersMode(filteredDiagramFiles, historyFiles, embeddingModel, documentSplitter)
            }
            RagMode.SIMULATION -> {
                simulateIndexing(filteredDiagramFiles, historyFiles, embeddingModel, documentSplitter)
            }
        }
    }

    /**
     * Determines RAG execution mode from multiple configuration sources.
     *
     * Priority order (highest to lowest):
     * 1. CLI parameter (`-Prag.mode`)
     * 2. Environment variable (`RAG_MODE`)
     * 3. Gradle property (`rag.mode` from -P flag or gradle.properties)
     * 4. Test mode property (`plantuml.test.rag.mode` via -P flag or gradle.properties)
     * 5. Config file (if databaseUrl is set → database, else → simulation)
     *
     * @param cliParams CLI parameters extracted from project properties
     * @param config PlantUML configuration with RAG database settings
     * @return Determined [RagMode] for this execution
     */
    private fun determineRagMode(cliParams: Map<String, Any?>, config: PlantumlConfig): RagMode {
        // Priority 1: CLI parameter (-Prag.mode)
        val cliMode = cliParams["rag.mode"]?.toString()?.lowercase()
        if (cliMode != null) {
            logger.lifecycle(PlantumlMessages.format("collect.rag_mode_cli", lang, cliMode))
            return RagMode.valueOf(cliMode.uppercase())
        }

        val envMode = System.getenv("RAG_MODE")?.lowercase()
        if (envMode != null) {
            logger.lifecycle(PlantumlMessages.format("collect.rag_mode_env", lang, envMode))
            return RagMode.valueOf(envMode.uppercase())
        }

        val gradlePropMode = project.properties["rag.mode"]?.toString()?.lowercase()
        if (gradlePropMode != null) {
            logger.lifecycle(PlantumlMessages.format("collect.rag_mode_prop", lang, gradlePropMode))
            return RagMode.valueOf(gradlePropMode.uppercase())
        }

        val testMode = project.properties["plantuml.test.rag.mode"]?.toString()?.lowercase()
            ?: System.getProperty("plantuml.test.rag.mode")?.lowercase()
        if (testMode != null) {
            logger.lifecycle(PlantumlMessages.format("collect.rag_mode_test", lang, testMode))
            return RagMode.valueOf(testMode.uppercase())
        }

        val useDatabase = config.rag.databaseUrl.isNotBlank() &&
                config.rag.port != 0 &&
                config.rag.username.isNotBlank() &&
                config.rag.password.isNotBlank()

        return if (useDatabase) {
            logger.lifecycle(PlantumlMessages.get("collect.rag_mode_db", lang))
            RagMode.DATABASE
        } else {
            logger.lifecycle(PlantumlMessages.get("collect.rag_mode_sim", lang))
            RagMode.SIMULATION
        }
    }

    /**
     * Executes RAG indexing using a production PostgreSQL database with pgvector extension.
     *
     * Connects to the configured PostgreSQL server and stores embeddings in the specified table.
     *
     * @param diagramFiles Array of PlantUML diagram files to index
     * @param historyFiles Array of LLM attempt history JSON files
     * @param config PlantUML configuration with database connection details
     * @param embeddingModel Model for generating 384-dimensional embeddings
     * @param documentSplitter Splitter for chunking documents into segments
     */
    private fun executeDatabaseMode(
        diagramFiles: Array<File>,
        historyFiles: Array<File>,
        config: PlantumlConfig,
        embeddingModel: EmbeddingModel,
        documentSplitter: DocumentSplitter
    ) {
        logger.lifecycle(PlantumlMessages.get("collect.using_db", lang))
        logger.lifecycle(PlantumlMessages.format("collect.db_url", lang, config.rag.databaseUrl, config.rag.port))

        // Initialize PGVector embedding store
        val embeddingStore: EmbeddingStore<TextSegment> = PgVectorEmbeddingStore.builder()
            .host(config.rag.databaseUrl)
            .port(config.rag.port)
            .database("plantuml_rag")
            .user(config.rag.username)
            .password(config.rag.password)
            .table(config.rag.tableName)
            .dimension(384)
            .build()

        indexDiagrams(diagramFiles, historyFiles, embeddingModel, documentSplitter, embeddingStore)

        logger.lifecycle(PlantumlMessages.format("collect.reindex_complete_db", lang, diagramFiles.size, historyFiles.size))
        logger.lifecycle(PlantumlMessages.get("collect.embeddings_stored_db", lang))
    }

    /**
     * Executes RAG indexing using a testcontainers PostgreSQL instance.
     *
     * Starts an ephemeral PostgreSQL container with pgvector extension,
     * indexes all diagrams, then stops the container. Ideal for integration tests.
     *
     * @param diagramFiles Array of PlantUML diagram files to index
     * @param historyFiles Array of LLM attempt history JSON files
     * @param embeddingModel Model for generating 384-dimensional embeddings
     * @param documentSplitter Splitter for chunking documents into segments
     */
    private fun executeTestcontainersMode(
        diagramFiles: Array<File>,
        historyFiles: Array<File>,
        embeddingModel: EmbeddingModel,
        documentSplitter: DocumentSplitter
    ) {
        logger.lifecycle(PlantumlMessages.get("collect.using_testcontainers", lang))

        val container = try {
            org.testcontainers.containers.PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
                start()
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            if (errorMsg.contains("port") || errorMsg.contains("bind") || errorMsg.contains("in use")) {
                val message = PlantumlMessages.get("collect.port_conflict", lang)
                logger.error(message)
                throw RuntimeException(message, e)
            }
            val message = PlantumlMessages.format("collect.container_failed", lang, e.message ?: "")
            logger.error(message)
            throw RuntimeException(message, e)
        }

        logger.lifecycle(PlantumlMessages.format("collect.container_started", lang, container.containerId))
        logger.lifecycle(PlantumlMessages.format("collect.jdbc_url", lang, container.jdbcUrl))

        val embeddingStore: EmbeddingStore<TextSegment> = PgVectorEmbeddingStore.builder()
            .host(container.host)
            .port(container.firstMappedPort)
            .database(container.databaseName)
            .user(container.username)
            .password(container.password)
            .table("embeddings")
            .dimension(384)
            .build()

        indexDiagrams(diagramFiles, historyFiles, embeddingModel, documentSplitter, embeddingStore)

        container.stop()
        logger.lifecycle(PlantumlMessages.get("collect.container_stopped", lang))
        logger.lifecycle(PlantumlMessages.format("collect.reindex_complete_tc", lang, diagramFiles.size, historyFiles.size))
        logger.lifecycle(PlantumlMessages.get("collect.embeddings_stored_tc", lang))
    }

    /**
     * Indexes PlantUML diagrams and training history files into the embedding store.
     *
     * For each file:
     * 1. Reads content
     * 2. Creates LangChain4j Document with metadata
     * 3. Splits into segments using [documentSplitter]
     * 4. Generates embeddings using [embeddingModel]
     * 5. Stores in [embeddingStore]
     *
     * @param diagramFiles PlantUML diagram files (`.puml`)
     * @param historyFiles LLM attempt history files (`.json`)
     * @param embeddingModel Model for generating embeddings
     * @param documentSplitter Splitter for chunking documents
     * @param embeddingStore Vector store for persisting embeddings
     */
    private fun indexDiagrams(
        diagramFiles: Array<File>,
        historyFiles: Array<File>,
        embeddingModel: EmbeddingModel,
        documentSplitter: DocumentSplitter,
        embeddingStore: EmbeddingStore<TextSegment>
    ) {
        diagramFiles.forEach { file ->
            logger.lifecycle(PlantumlMessages.format("collect.indexing_diagram", lang, file.name))

            val content = try {
                file.readText()
            } catch (e: Exception) {
                handleFileReadError(file, e)
            }

            val doc = document(
                content,
                metadata("source", file.name)
                    .put("type", "plantuml")
                    .put("contentType", "diagram")
            )

            val segments = documentSplitter.split(doc)
            logger.lifecycle(PlantumlMessages.format("collect.split_segments", lang, segments.size))

            segments.forEach { segment ->
                val embedding: Embedding = embeddingModel.embed(segment.text()).content()
                try {
                    embeddingStore.add(embedding, segment)
                    logger.lifecycle(PlantumlMessages.format("collect.stored_embedding", lang, segment.text().take(50)))
                } catch (e: Exception) {
                    handleEmbeddingStoreError(e, file)
                }
            }
        }

        historyFiles.forEach { file ->
            logger.lifecycle(PlantumlMessages.format("collect.indexing_history", lang, file.name))

            val content = try {
                file.readText()
            } catch (e: Exception) {
                handleFileReadError(file, e)
            }

            val doc = document(
                content,
                metadata("source", file.name)
                    .put("type", "training")
                    .put("contentType", "history")
            )

            val segments = documentSplitter.split(doc)
            logger.lifecycle(PlantumlMessages.format("collect.split_segments", lang, segments.size))

            segments.forEach { segment ->
                val embedding: Embedding = embeddingModel.embed(segment.text()).content()
                try {
                    embeddingStore.add(embedding, segment)
                    logger.lifecycle(PlantumlMessages.format("collect.stored_embedding", lang, segment.text().take(50)))
                } catch (e: Exception) {
                    handleEmbeddingStoreError(e, file)
                }
            }
        }
    }

    /**
     * Handles file read errors, including disk space issues.
     */
    private fun handleFileReadError(file: File, e: Exception): Nothing {
        val errorMsg = e.message ?: "Unknown error"
        if (errorMsg.contains("space") || errorMsg.contains("No space left on device")) {
            val message = PlantumlMessages.format("collect.disk_space_read", lang, file.name, e.message ?: "")
            logger.error(message)
            throw RuntimeException(message, e)
        }
        val message = PlantumlMessages.format("collect.failed_read", lang, file.name, e.message ?: "")
        logger.error(message)
        throw RuntimeException(message, e)
    }

    /**
     * Handles embedding store errors, including disk space issues during write operations.
     */
    private fun handleEmbeddingStoreError(e: Exception, context: File) {
        val errorMsg = e.message ?: "Unknown error"
        if (errorMsg.contains("space") || errorMsg.contains("No space left on device") ||
            errorMsg.contains("disk") || errorMsg.contains("storage")) {
            val message = PlantumlMessages.format("collect.disk_space_store", lang, context.name, e.message ?: "")
            logger.error(message)
            cleanupPartialOutputs()
            throw RuntimeException(message, e)
        }
        val message = PlantumlMessages.format("collect.failed_store", lang, context.name, e.message ?: "")
        logger.error(message)
        throw RuntimeException(message, e)
    }

    /**
     * Cleans up partial output files when an error occurs.
     */
    private fun cleanupPartialOutputs() {
        try {
            val buildDir = File(project.buildDir, "plantuml-plugin")
            if (buildDir.exists()) {
                logger.lifecycle(PlantumlMessages.format("collect.cleaning", lang, buildDir.absolutePath))
                buildDir.deleteRecursively()
                logger.lifecycle(PlantumlMessages.get("collect.cleanup_complete", lang))
            }
        } catch (e: Exception) {
            logger.warn(PlantumlMessages.format("collect.cleanup_failed", lang, e.message ?: "Unknown error"))
        }
    }

    /**
     * Simulates RAG indexing without persisting embeddings to a database.
     *
     * Processes all diagrams and history files through the embedding pipeline
     * (document creation, splitting, embedding generation) but does not store
     * the results. Useful for testing and demonstration purposes.
     *
     * @param diagramFiles Array of PlantUML diagram files to process
     * @param historyFiles Array of LLM attempt history JSON files
     * @param embeddingModel Model for generating 384-dimensional embeddings
     * @param documentSplitter Splitter for chunking documents into segments
     */
    private fun simulateIndexing(
        diagramFiles: Array<File>,
        historyFiles: Array<File>,
        embeddingModel: EmbeddingModel,
        documentSplitter: DocumentSplitter
    ) {
        // Check for test mode disk space simulation
        val simulateDiskFull = System.getProperty("plantuml.test.disk.full") == "true"
        
        if (simulateDiskFull) {
            val message = PlantumlMessages.get("collect.disk_space_sim", lang)
            logger.error(message)
            cleanupPartialOutputs()
            throw RuntimeException(message)
        }
        
        // For now, we'll just log the indexing process
        // In a production implementation, this would connect to a vector database
        diagramFiles.forEach { file ->
            logger.lifecycle(PlantumlMessages.format("collect.indexing_diagram", lang, file.name))

            val content = file.readText()

            val document = document(
                content,
                metadata("source", file.name).put("type", "plantuml").put("contentType", "diagram")
            )


            val segments = documentSplitter.split(document)
            logger.lifecycle(PlantumlMessages.format("collect.split_segments", lang, segments.size))

            segments.forEach { segment ->
                val embedding: Embedding = embeddingModel.embed(segment.text()).content()
                logger.lifecycle(PlantumlMessages.format("collect.generated_embedding", lang, segment.text().take(50)))
            }
        }

        historyFiles.forEach { file ->
            logger.lifecycle(PlantumlMessages.format("collect.indexing_history", lang, file.name))

            val content = file.readText()

            val document = document(
                content,
                metadata("source", file.name).put("type", "training").put("contentType", "history")
            )

            val segments = documentSplitter.split(document)
            logger.lifecycle(PlantumlMessages.format("collect.split_segments", lang, segments.size))

            segments.forEach { segment ->
                val embedding: Embedding = embeddingModel.embed(segment.text()).content()
                logger.lifecycle(PlantumlMessages.format("collect.generated_embedding", lang, segment.text().take(50)))
            }
        }

        logger.lifecycle(PlantumlMessages.format("collect.reindex_complete_sim", lang, diagramFiles.size, historyFiles.size))
        logger.lifecycle(PlantumlMessages.get("collect.note_production", lang))
        logger.lifecycle(PlantumlMessages.get("collect.note_pgvector", lang))
        logger.lifecycle(PlantumlMessages.get("collect.note_config", lang))
    }
}


