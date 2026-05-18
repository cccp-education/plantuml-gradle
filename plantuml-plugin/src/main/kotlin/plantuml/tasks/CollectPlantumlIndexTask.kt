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

    init {
        group = "collect"
        description = "Rebuilds the RAG index with collected PlantUML diagrams"
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
        logger.lifecycle("Rebuilding RAG index with PlantUML diagrams...")

        // Check for test mode port conflict simulation FIRST, before determining RAG mode
        val simulatePortConflict = System.getProperty("plantuml.test.simulate.port.conflict") == "true" ||
            project.properties["plantuml.test.simulate.port.conflict"]?.toString() == "true"
        if (simulatePortConflict) {
            val message = """
                Failed to start pgvector container: port 5432 is already in use.
                Suggestions:
                - Use a different port: ./gradlew collectPlantumlIndex -Pplantuml.rag.port=5433
                - Stop existing PostgreSQL: sudo systemctl stop postgresql
                - Check running containers: docker ps | grep postgres
            """.trimIndent()
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
            logger.lifecycle("  → No RAG directory found at: ${ragDir.absolutePath}")
            ragDir.mkdirs()
            logger.lifecycle("  → Created RAG directory")
            return
        }

        // Check if the path is actually a directory
        if (!ragDir.isDirectory) {
            val errorMsg = "  ✗ Path exists but is not a directory: ${ragDir.absolutePath}"
            logger.error(errorMsg)
            throw RuntimeException(errorMsg)
        }

        // Check if we can read the directory
        if (!ragDir.canRead()) {
            val errorMsg = "  ✗ Permission denied: Cannot read RAG directory at ${ragDir.absolutePath}"
            logger.error(errorMsg)
            throw RuntimeException(errorMsg)
        }

        val diagramFiles = try {
            ragDir.listFiles { file ->
                file.extension == "puml"
            } ?: emptyArray()
        } catch (e: SecurityException) {
            val errorMsg =
                "  ✗ Permission denied: Cannot access files in RAG directory ${ragDir.absolutePath} - ${e.message}"
            logger.error(errorMsg)
            throw RuntimeException(errorMsg, e)
        } catch (e: Exception) {
            // Handle other potential exceptions like AccessDeniedException
            val errorMsg = "  ✗ Error accessing RAG directory ${ragDir.absolutePath} - ${e.message}"
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
                val errorMsg =
                    "  ✗ Permission denied: Cannot access training directory ${trainingDir.absolutePath} - ${e.message}"
                logger.error(errorMsg)
                throw RuntimeException(errorMsg, e)
            }
        } else {
            emptyArray()
        }

        if (diagramFiles.isEmpty() && historyFiles.isEmpty()) {
            logger.lifecycle("  → No PlantUML diagrams or training data found in RAG directory")
            return
        }

        logger.lifecycle("  → Found ${diagramFiles.size} PlantUML diagrams and ${historyFiles.size} training histories for indexing")

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
                executeDatabaseMode(diagramFiles, historyFiles, config, embeddingModel, documentSplitter)
            }
            RagMode.TESTCONTAINERS -> {
                executeTestcontainersMode(diagramFiles, historyFiles, embeddingModel, documentSplitter)
            }
            RagMode.SIMULATION -> {
                simulateIndexing(diagramFiles, historyFiles, embeddingModel, documentSplitter)
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
            logger.lifecycle("  → RAG mode from CLI parameter: $cliMode")
            return RagMode.valueOf(cliMode.uppercase())
        }

        // Priority 2: Environment variable (RAG_MODE)
        val envMode = System.getenv("RAG_MODE")?.lowercase()
        if (envMode != null) {
            logger.lifecycle("  → RAG mode from environment variable: $envMode")
            return RagMode.valueOf(envMode.uppercase())
        }

        // Priority 3: Gradle property (rag.mode from -P flag or gradle.properties)
        val gradlePropMode = project.properties["rag.mode"]?.toString()?.lowercase()
        if (gradlePropMode != null) {
            logger.lifecycle("  → RAG mode from Gradle property: $gradlePropMode")
            return RagMode.valueOf(gradlePropMode.uppercase())
        }

        // Priority 4: Test mode property (plantuml.test.rag.mode via -P flag or gradle.properties)
        val testMode = project.properties["plantuml.test.rag.mode"]?.toString()?.lowercase()
            ?: System.getProperty("plantuml.test.rag.mode")?.lowercase()
        if (testMode != null) {
            logger.lifecycle("  → RAG mode from test property: $testMode")
            return RagMode.valueOf(testMode.uppercase())
        }

        // Priority 5: Config file (if databaseUrl is set → database, else → simulation)
        val useDatabase = config.rag.databaseUrl.isNotBlank() &&
                config.rag.port != 0 &&
                config.rag.username.isNotBlank() &&
                config.rag.password.isNotBlank()

        return if (useDatabase) {
            logger.lifecycle("  → RAG mode from config file: database")
            RagMode.DATABASE
        } else {
            logger.lifecycle("  → RAG mode from config file: simulation")
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
        logger.lifecycle("  → Using PostgreSQL database for RAG indexing")
        logger.lifecycle("  → Database URL: ${config.rag.databaseUrl}:${config.rag.port}")

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

        logger.lifecycle("  ✓ RAG reindexing complete with ${diagramFiles.size} diagrams and ${historyFiles.size} histories")
        logger.lifecycle("  → Embeddings stored in PostgreSQL database")
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
        logger.lifecycle("  → Using testcontainers PostgreSQL for RAG indexing")

        val container = try {
            org.testcontainers.containers.PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
                start()
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            if (errorMsg.contains("port") || errorMsg.contains("bind") || errorMsg.contains("in use")) {
                val message = """
                    Failed to start pgvector container: port 5432 is already in use.
                    Suggestions:
                    - Use a different port: ./gradlew collectPlantumlIndex -Pplantuml.rag.port=5433
                    - Stop existing PostgreSQL: sudo systemctl stop postgresql
                    - Check running containers: docker ps | grep postgres
                """.trimIndent()
                logger.error(message)
                throw RuntimeException(message, e)
            }
            val message = "Failed to start pgvector container: ${e.message}"
            logger.error(message)
            throw RuntimeException(message, e)
        }

        logger.lifecycle("  → PostgreSQL container started: ${container.containerId}")
        logger.lifecycle("  → JDBC URL: ${container.jdbcUrl}")

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
        logger.lifecycle("  → PostgreSQL container stopped")
        logger.lifecycle("  ✓ RAG reindexing complete with ${diagramFiles.size} diagrams and ${historyFiles.size} histories")
        logger.lifecycle("  → Embeddings stored in testcontainers PostgreSQL")
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
            logger.lifecycle("    Indexing diagram: ${file.name}")

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
            logger.lifecycle("      Split into ${segments.size} segments")

            segments.forEach { segment ->
                val embedding: Embedding = embeddingModel.embed(segment.text()).content()
                try {
                    embeddingStore.add(embedding, segment)
                    logger.lifecycle("        Stored embedding for segment: ${segment.text().take(50)}...")
                } catch (e: Exception) {
                    handleEmbeddingStoreError(e, file)
                }
            }
        }

        historyFiles.forEach { file ->
            logger.lifecycle("    Indexing training history: ${file.name}")

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
            logger.lifecycle("      Split into ${segments.size} segments")

            segments.forEach { segment ->
                val embedding: Embedding = embeddingModel.embed(segment.text()).content()
                try {
                    embeddingStore.add(embedding, segment)
                    logger.lifecycle("        Stored embedding for segment: ${segment.text().take(50)}...")
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
            val message = """
                Disk space exhausted while reading ${file.name}.
                Error: ${e.message}
                Suggestions:
                - Free up disk space
                - Check available storage: df -h
                - Clean temporary files
            """.trimIndent()
            logger.error(message)
            throw RuntimeException(message, e)
        }
        val message = "Failed to read file ${file.name}: ${e.message}"
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
            val message = """
                Disk space exhausted while storing embeddings for ${context.name}.
                Error: ${e.message}
                Suggestions:
                - Free up disk space immediately
                - Check available storage: df -h
                - Clean up partial outputs from build directory
                Cleanup: Removing partial files...
            """.trimIndent()
            logger.error(message)
            cleanupPartialOutputs()
            throw RuntimeException(message, e)
        }
        val message = "Failed to store embedding for ${context.name}: ${e.message}"
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
                logger.lifecycle("  → Cleaning up partial outputs in ${buildDir.absolutePath}")
                buildDir.deleteRecursively()
                logger.lifecycle("  ✓ Cleanup complete")
            }
        } catch (e: Exception) {
            logger.warn("  ⚠ Cleanup failed: ${e.message}")
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
            val message = """
                Disk space exhausted: No space left on device.
                Error: Simulated disk full condition for testing.
                Suggestions:
                - Free up disk space
                - Check available storage: df -h
                - Clean up partial outputs from build directory
                Cleanup: Removing partial files...
            """.trimIndent()
            logger.error(message)
            cleanupPartialOutputs()
            throw RuntimeException(message)
        }
        
        // For now, we'll just log the indexing process
        // In a production implementation, this would connect to a vector database
        diagramFiles.forEach { file ->
            logger.lifecycle("    Indexing diagram: ${file.name}")

            // Read the PlantUML diagram
            val content = file.readText()

            // Create document
            val document = document(
                content,
                metadata("source", file.name).put("type", "plantuml").put("contentType", "diagram")
            )


            // Split document into segments
            val segments = documentSplitter.split(document)
            logger.lifecycle("      Split into ${segments.size} segments")

            // Generate embeddings (in a real implementation, we would store these)
            segments.forEach { segment ->
                val embedding: Embedding = embeddingModel.embed(segment.text()).content()
                logger.lifecycle("        Generated embedding for segment: ${segment.text().take(50)}...")
            }
        }

        // Process each history file
        historyFiles.forEach { file ->
            logger.lifecycle("    Indexing training history: ${file.name}")

            // Read the history file
            val content = file.readText()

            // Create document
            val document = document(
                content,
                metadata("source", file.name).put("type", "training").put("contentType", "history")
            )

            // Split document into segments
            val segments = documentSplitter.split(document)
            logger.lifecycle("      Split into ${segments.size} segments")

            // Generate embeddings (in a real implementation, we would store these)
            segments.forEach { segment ->
                val embedding: Embedding = embeddingModel.embed(segment.text()).content()
                logger.lifecycle("        Generated embedding for segment: ${segment.text().take(50)}...")
            }
        }

        logger.lifecycle("  ✓ RAG reindexing complete with ${diagramFiles.size} diagrams and ${historyFiles.size} histories")
        logger.lifecycle("  → Note: In a production implementation, embeddings would be stored in a vector database")
        logger.lifecycle("  → To enable actual vector storage, configure PostgreSQL with pgvector extension")
        logger.lifecycle("  → Set database connection properties in plantuml-context.yml")
    }
}


