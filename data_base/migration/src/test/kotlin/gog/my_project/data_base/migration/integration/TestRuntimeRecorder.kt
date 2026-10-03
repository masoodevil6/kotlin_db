package gog.my_project.data_base.migration.integration

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.time.Instant
import java.util.UUID

/** Best-effort, run-scoped observations for Help. Recorder failures never change a test result. */
internal class TestRuntimeRecorder private constructor(
    private val projectPath: String,
    private val testId: String,
    private val runId: String,
    private val projectRoot: Path,
    private val artifact: Path,
    stepIds: List<String>,
    parentIds: Map<String, String>,
) {
    private data class Step(
        val id: String,
        val order: Int,
        val parentId: String?,
        var status: String = "not_started",
        var startedAt: String? = null,
        var completedAt: String? = null,
        var startedNanos: Long? = null,
        var durationMs: Long? = null,
        var data: Map<String, Any?>? = null,
    )

    private data class Migration(
        val migrationId: String,
        val registrationOrder: Int,
        val operationType: String = "not_collected",
        val target: String = "not_collected",
        var executionStatus: String = "unknown",
        var historyStatus: String = "unknown",
        var batch: Int? = null,
    )

    private val steps = stepIds.mapIndexed { index, id -> Step(id, index + 1, parentIds[id]) }.toMutableList()
    private val migrations = linkedMapOf<String, Migration>()
    private var activeStep: Step? = null
    private var startedAt: String? = null
    private var completedAt: String? = null
    private var runtimeStatus = "incomplete"
    private var historyVerification: Map<String, Any?>? = null
    private var schemaVerification: Map<String, Any?>? = null
    private var finalState: Map<String, Any?>? = null
    private var hasCheckpoint = false
    private var enabled = true

    fun begin() = safely {
        startedAt = Instant.now().toString()
    }

    fun startStep(id: String) = safely {
        val step = steps.single { it.id == id }
        activeStep = step
        step.status = "in_progress"
      step.startedAt = Instant.now().toString()
      step.startedNanos = System.nanoTime()
      hasCheckpoint = true
      publish()
    }

    fun succeedStep(id: String, data: Map<String, Any?>? = null) = safely {
        val step = steps.single { it.id == id }
        step.status = "succeeded"
        step.completedAt = Instant.now().toString()
        step.durationMs = step.startedNanos?.let { (System.nanoTime() - it) / 1_000_000 }
        step.data = data
        if (activeStep === step) activeStep = null
        publish()
    }

    fun failActiveStep() = safely {
        val step = activeStep ?: return@safely
        step.status = "failed"
        step.completedAt = Instant.now().toString()
        step.durationMs = step.startedNanos?.let { (System.nanoTime() - it) / 1_000_000 }
        step.data = mapOf("failureType" to "test_failure")
        activeStep = null
        markRemainingNotExecuted(step.order)
        publish()
    }

    fun markRemainingNotExecuted(afterOrder: Int) = safely {
        steps.filter { it.order > afterOrder && it.status == "not_started" }.forEach { it.status = "not_executed" }
        publish()
    }

    fun recordMigrations(
        ids: List<String>,
        historyRows: List<Pair<String, Int>>,
        failedId: String? = null,
        notExecutedIds: Set<String> = emptySet(),
        historyWasRead: Boolean = true,
        executionSucceeded: Boolean = false,
    ) = safely {
        val history = historyRows.associate { it.first to it.second }
        ids.forEachIndexed { index, id ->
            val migration = migrations.getOrPut(id) { Migration(id, index + 1) }
            val batch = history[id]
            when {
                batch != null -> { migration.executionStatus = "succeeded"; migration.historyStatus = "recorded"; migration.batch = batch }
                id == failedId -> { migration.executionStatus = "failed"; migration.historyStatus = if (historyWasRead) "not_recorded" else "unknown" }
                id in notExecutedIds -> { migration.executionStatus = "not_executed"; migration.historyStatus = "not_applicable" }
                executionSucceeded && historyWasRead -> { migration.executionStatus = "succeeded"; migration.historyStatus = "failed" }
                historyWasRead -> { migration.executionStatus = "unknown"; migration.historyStatus = "not_recorded" }
            }
        }
        if (historyWasRead) historyVerification = mapOf("rows" to historyRows.map { mapOf("migrationId" to it.first, "batch" to it.second) })
        publish()
    }

    fun recordSchema(observation: Map<String, Any?>, observedState: Map<String, Any?> = observation) = safely {
        schemaVerification = observation
        finalState = observedState
        publish()
    }

    fun recordFinalState(observedState: Map<String, Any?>) = safely {
        finalState = observedState
        publish()
    }

    fun recordHistory(rows: List<Pair<String, Int>>) = safely {
        historyVerification = mapOf("rows" to rows.map { mapOf("migrationId" to it.first, "batch" to it.second) })
        publish()
    }

    fun complete() = safely {
        completedAt = Instant.now().toString()
        runtimeStatus = "complete"
        publish()
    }

    fun incomplete() = safely {
        failActiveStep()
        runtimeStatus = "incomplete"
        completedAt = Instant.now().toString()
        if (hasCheckpoint) publish()
    }

    private inline fun safely(block: () -> Unit) {
        if (!enabled) return
        try { block() } catch (_: Exception) { enabled = false }
    }

    private fun publish() {
        val expectedParent = projectRoot.toRealPath().resolve(Path.of("build", "reports", "help", "runtime", runId)).normalize()
        require(artifact.parent.toRealPath() == expectedParent)
        val payload = linkedMapOf<String, Any?>(
            "schemaVersion" to 2,
            "projectPath" to projectPath,
            "testId" to testId,
            "runId" to runId,
            "startedAt" to startedAt,
            "runtimeStatus" to runtimeStatus,
            "steps" to steps.map { step ->
                linkedMapOf<String, Any?>(
                    "id" to step.id, "order" to step.order, "status" to step.status,
                    "parentId" to step.parentId,
                    "startedAt" to step.startedAt, "completedAt" to step.completedAt,
                    "durationMs" to step.durationMs, "data" to step.data,
                ).filterValues { it != null }
            },
            "migrations" to migrations.values.map { migration ->
                linkedMapOf<String, Any?>(
                    "migrationId" to migration.migrationId,
                    "registrationOrder" to migration.registrationOrder,
                    "operationType" to migration.operationType,
                    "target" to migration.target,
                    "executionStatus" to migration.executionStatus,
                    "historyStatus" to migration.historyStatus,
                    "batch" to migration.batch,
                ).filterValues { it != null }
            },
        )
        completedAt?.let { payload["completedAt"] = it }
        schemaVerification?.let { payload["schemaVerification"] = it }
        historyVerification?.let { payload["historyVerification"] = it }
        finalState?.let { payload["finalState"] = it }
        val json = encodeJson(payload).toByteArray(StandardCharsets.UTF_8)
        val staging = Files.createTempFile(artifact.parent, ".runtime-checkpoint-", ".tmp")
        try {
            Files.write(staging, json)
            Files.move(staging, artifact, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: AtomicMoveNotSupportedException) {
            Files.deleteIfExists(staging)
            throw error
        } catch (error: Exception) {
            Files.deleteIfExists(staging)
            throw error
        }
    }

    private fun encodeJson(value: Any?): String = when (value) {
        null -> "null"
        is String -> buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
                }
            }
            append('"')
        }
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, item) -> "${encodeJson(key.toString())}:${encodeJson(item)}" }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { encodeJson(it) }
        else -> error("Unsupported JSON value")
    }

    companion object {
        const val RUN_ID_PROPERTY = "help.runtime.runId"
        const val PATH_PROPERTY = "help.runtime.path"
        const val PROJECT_PATH = ":data_base:migration"
        const val SYSTEM_TEST_ID = "migration-system-integration"
        const val SCHEMA_TEST_ID = "migration-schema-integration"

        fun create(
            projectPath: String,
            testId: String,
            stepIds: List<String>,
            parentIds: Map<String, String> = emptyMap(),
            suppliedRunId: String? = System.getProperty(RUN_ID_PROPERTY),
            suppliedPath: String? = System.getProperty(PATH_PROPERTY),
            buildRootOverride: Path? = null,
        ): TestRuntimeRecorder? {
            val runId = suppliedRunId ?: return null
            val runtimeFile = suppliedPath ?: return null
            return try {
                require(projectPath == PROJECT_PATH)
                require(testId == SYSTEM_TEST_ID || testId == SCHEMA_TEST_ID)
                require(runId == UUID.fromString(runId).toString())
                val artifact = Path.of(runtimeFile).toAbsolutePath().normalize()
                val suffix = Path.of("build", "reports", "help", "runtime", runId, "runtime.json")
                require(artifact.endsWith(suffix))
                // Remove the fixed build/reports/help/runtime/<runId>/runtime.json suffix.
                var projectRoot = artifact
                repeat(suffix.nameCount) { projectRoot = projectRoot.parent }
                val canonicalRoot = projectRoot.toRealPath()
                require(canonicalRoot == expectedProjectRoot(projectPath, buildRootOverride))
                val canonicalExpected = canonicalRoot.resolve(suffix).normalize()
                require(artifact == canonicalExpected)
                require(stepIds.distinct().size == stepIds.size)
                require(parentIds.keys.all { it in stepIds })
                require(parentIds.values.all { it.isNotBlank() })
                TestRuntimeRecorder(projectPath, testId, runId, canonicalRoot, artifact, stepIds, parentIds)
            } catch (_: Exception) {
                null
            }
        }

        private fun expectedProjectRoot(projectPath: String, buildRootOverride: Path?): Path {
            var buildRoot = buildRootOverride?.toRealPath() ?: Path.of(System.getProperty("user.dir")).toRealPath()
            while (!Files.exists(buildRoot.resolve("settings.gradle.kts")) && !Files.exists(buildRoot.resolve("settings.gradle"))) {
                buildRoot = buildRoot.parent ?: error("Cannot locate the Gradle build root from the Test JVM")
            }
            val relativeProject = projectPath.split(':').filter(String::isNotEmpty)
            return relativeProject.fold(buildRoot) { current, segment -> current.resolve(segment) }.toRealPath()
        }

        fun captureSchema(connection: Connection, tables: List<String>, includeRelationalDetails: Boolean = false): Map<String, Any?> {
            val metadata = connection.metaData
            val snapshots = tables.map { table ->
                val columns = metadata.getColumns(connection.catalog, null, table, "%").use { rows ->
                    buildList {
                        while (rows.next()) add(
                            mapOf(
                                "name" to rows.getString("COLUMN_NAME"),
                                "jdbcType" to rows.getInt("DATA_TYPE"),
                                "nullable" to (rows.getInt("NULLABLE") != java.sql.DatabaseMetaData.columnNoNulls),
                                "autoIncrement" to (rows.getString("IS_AUTOINCREMENT") == "YES"),
                            ),
                        )
                    }
                }
                val primaryKey = if (includeRelationalDetails) metadata.getPrimaryKeys(connection.catalog, null, table).use { rows ->
                    buildList { while (rows.next()) add(rows.getInt("KEY_SEQ") to rows.getString("COLUMN_NAME")) }
                }.sortedBy { it.first }.map { it.second } else emptyList()
                val indexes = if (includeRelationalDetails) metadata.getIndexInfo(connection.catalog, null, table, false, false).use { rows ->
                    val grouped = linkedMapOf<String, MutableMap<String, Any?>>()
                    while (rows.next()) {
                        val name = rows.getString("INDEX_NAME") ?: continue
                        val column = rows.getString("COLUMN_NAME") ?: continue
                        val item = grouped.getOrPut(name) {
                            mutableMapOf("name" to name, "unique" to !rows.getBoolean("NON_UNIQUE"), "columns" to mutableListOf<String>())
                        }
                        @Suppress("UNCHECKED_CAST")
                        (item.getValue("columns") as MutableList<String>).add(column)
                    }
                    grouped.values.toList()
                } else emptyList()
                val foreignKeys = if (includeRelationalDetails) metadata.getImportedKeys(connection.catalog, null, table).use { rows ->
                    buildList {
                        while (rows.next()) add(
                            mapOf(
                                "name" to rows.getString("FK_NAME"),
                                "sequence" to rows.getInt("KEY_SEQ"),
                                "column" to rows.getString("FKCOLUMN_NAME"),
                                "referencedTable" to rows.getString("PKTABLE_NAME"),
                                "referencedColumn" to rows.getString("PKCOLUMN_NAME"),
                            ),
                        )
                    }.sortedBy { it["sequence"] as Int }
                } else emptyList()
                mapOf("name" to table, "columns" to columns, "primaryKey" to primaryKey, "indexes" to indexes, "foreignKeys" to foreignKeys)
            }
            return mapOf("tables" to snapshots)
        }
    }
}
