package gog.my_project.data_base_migration.executor.service.run

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_migration.api.builder.migrationConfig
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.api.registration.group.SingleMigration
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_migration.core.tags.MigrationItemTag
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.executor.controller.MigrationController
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionTransactionService
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDataType
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.ArrayDeque
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MigrationRunServiceTest {
    private val service = MigrationRunService()
    private lateinit var database: FakeDatabase
    private lateinit var scope: OperationExecutionScope
    private val registration = RegisteredMigration(
        MigrationIdentity.fromValue(MigrationItem.value),
        SampleMigration::class,
    )

    @BeforeTest
    fun setUp() {
        SampleMigration.reset()
        VersionMigrationTrace.reset()
        database = FakeDatabase()
        scope = OperationExecutionScope(
            database.connection,
            DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
        )
    }

    @Test
    fun `runUp executes one migration then inserts tag and applied record in the supplied scope`() {
        database.respond(
            emptyList(), // Migration Tag is not present before up.
            emptyList(), // Migration Tag is still absent after up, so it is inserted.
            emptyList(), // No conflicting applied history exists.
        )

        val result = service.runUp(VERSION_ID, scope, registration)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(1, SampleMigration.upCalls)
        assertEquals(0, SampleMigration.downCalls)
        assertEquals(
            listOf("read", "ddl", "read", "insert-tag", "read", "insert-history"),
            database.events.map { it.substringBefore(':') },
        )
        val readSql = database.executedSql.filter { it.contains("SELECT", ignoreCase = true) }
        assertTrue(readSql.any { it.contains("system_migration_tag", ignoreCase = true) })
        assertTrue(readSql.any {
            it.contains("system_migration", ignoreCase = true) &&
                !it.contains("system_migration_tag", ignoreCase = true)
        })
        assertTrue(readSql.none { it.contains("WITH", ignoreCase = true) })
        assertTrue(database.executedSql.none { it.contains("system_migration_version", ignoreCase = true) })
        assertEquals(listOf(MigrationItem.value, MIGRATION_TAG_TYPE), database.readParameters[0])
        assertEquals(listOf(MigrationItem.value, MIGRATION_TAG_TYPE), database.readParameters[1])
        assertEquals(listOf(GENERATED_TAG_ID.toInt()), database.readParameters[2])
        assertEquals(listOf(MigrationItem.value, MIGRATION_TAG_TYPE), database.dmlCalls[0].parameters)
        assertEquals(listOf(GENERATED_TAG_ID.toInt(), VERSION_ID), database.dmlCalls[1].parameters)
        val historyInsert = database.executedSql.last { it.contains("INSERT", ignoreCase = true) &&
            it.contains("system_migration", ignoreCase = true) &&
            !it.contains("system_migration_tag", ignoreCase = true) }
        assertTrue(historyInsert.contains("tag_id", ignoreCase = true))
        assertTrue(historyInsert.contains("version_id", ignoreCase = true))
        // Every read, DDL, and DML statement is prepared through this one caller-owned Connection.
        assertTrue(database.connectionPreparationCount > 1)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(0, database.connectionCloseCount)
        assertTrue(database.statementCloseCount > 0)
        assertTrue(database.resultSetCloseCount > 0)
    }

    @Test
    fun `runUp reuses an existing item Tag with no applied history`() {
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            emptyList(), // The existing Item Tag has no applied history yet.
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            emptyList(), // No conflicting history appeared while up() ran.
        )

        val result = service.runUp(VERSION_ID, scope, registration)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(1, SampleMigration.upCalls)
        assertEquals(1, database.dmlCalls.size)
        val historyInsert = database.dmlCalls.single()
        assertTrue(historyInsert.sql.contains("INSERT", ignoreCase = true))
        assertTrue(historyInsert.sql.contains("system_migration", ignoreCase = true))
        assertTrue(!historyInsert.sql.contains("system_migration_tag", ignoreCase = true))
        assertEquals(listOf(MIGRATION_TAG_ID, VERSION_ID), historyInsert.parameters)
    }

    @Test
    fun `failed up callback leaves migration history untouched`() {
        val failure = IllegalArgumentException("up failed")
        SampleMigration.upFailure = failure
        database.respond(emptyList())

        val result = service.runUp(VERSION_ID, scope, registration)

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, SampleMigration.upCalls)
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.connectionCloseCount)
    }

    @Test
    fun `failed schema DDL propagates and prevents tag and history writes`() {
        database.failDdl = true
        database.respond(emptyList())

        val result = service.runUp(VERSION_ID, scope, registration)

        assertIs<SQLException>(assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, SampleMigration.upCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `failed tag insert prevents applied history insert and preserves JDBC failure`() {
        database.failDmlCalls += 1
        database.respond(
            emptyList(),
            emptyList(),
        )

        val result = service.runUp(VERSION_ID, scope, registration)

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertIs<SQLException>(failure.exception)
        assertSame(failure.exception, scope.firstFailure)
        assertEquals(1, database.dmlCount)
        assertTrue(database.events.none { it.startsWith("insert-history:") })
    }

    @Test
    fun `failed history insert is returned after successful tag creation`() {
        database.failDmlCalls += 2
        database.respond(
            emptyList(),
            emptyList(),
            emptyList(),
        )

        val result = service.runUp(VERSION_ID, scope, registration)

        assertIs<SQLException>(assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(2, database.dmlCount)
        assertEquals(1, database.insertHistoryCount)
    }

    @Test
    fun `runUp rejects existing history without invoking the migration again`() {
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
        )

        val result = service.runUp(VERSION_ID, scope, registration)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, SampleMigration.upCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `runDown executes callback then removes only matching history and unreferenced item tag`() {
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
            emptyList(),
        )

        val result = service.runDown(VERSION_ID, scope, registration)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(1, SampleMigration.downCalls)
        assertEquals(0, SampleMigration.upCalls)
        assertEquals(2, database.deleteCount)
        assertEquals(1, database.deleteHistoryCount)
        assertEquals(1, database.deleteTagCount)
        assertTrue(database.executedSql.none { it.contains("system_migration_version", ignoreCase = true) })
        val historyDelete = database.dmlCalls.first { it.sql.contains("system_migration", ignoreCase = true) &&
            !it.sql.contains("system_migration_tag", ignoreCase = true) }
        assertTrue(historyDelete.sql.contains("tag_id", ignoreCase = true))
        assertTrue(historyDelete.sql.contains("version_id", ignoreCase = true))
        assertEquals(listOf(MIGRATION_TAG_ID, VERSION_ID), historyDelete.parameters)
        val tagDelete = database.dmlCalls.last()
        assertTrue(tagDelete.sql.contains("DELETE", ignoreCase = true))
        assertTrue(tagDelete.sql.contains("system_migration_tag", ignoreCase = true))
        assertTrue(tagDelete.sql.contains("id", ignoreCase = true))
        assertTrue(tagDelete.sql.contains("WHERE", ignoreCase = true))
        assertTrue(!tagDelete.sql.contains("tagName", ignoreCase = true))
        assertTrue(!tagDelete.sql.contains("tagType", ignoreCase = true))
        assertEquals(listOf(MIGRATION_TAG_ID), tagDelete.parameters)
        assertTrue(database.events.indexOfFirst { it.startsWith("ddl:") } <
            database.events.indexOfFirst { it.startsWith("delete-history:") })
        assertTrue(database.events.indexOfFirst { it.startsWith("delete-history:") } <
            database.events.indexOfFirst { it.startsWith("delete-tag:") })
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(0, database.connectionCloseCount)
    }

    @Test
    fun `runDown rejects missing applied history before invoking callback`() {
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            emptyList(),
        )

        val result = service.runDown(VERSION_ID, scope, registration)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, SampleMigration.downCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `runDown rejects history recorded under a different version`() {
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = OTHER_VERSION_ID),
        )

        val result = service.runDown(VERSION_ID, scope, registration)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, SampleMigration.downCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `runDown rejects a tag of the wrong category before invoking callback`() {
        database.respond(rows(id = MIGRATION_TAG_ID, tagType = VERSION_TAG_TYPE))

        val result = service.runDown(VERSION_ID, scope, registration)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, SampleMigration.downCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `failed down callback leaves history unchanged`() {
        val failure = IllegalStateException("down failed")
        SampleMigration.downFailure = failure
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
        )

        val result = service.runDown(VERSION_ID, scope, registration)

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, SampleMigration.downCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `failed history deletion prevents tag deletion`() {
        database.failDmlCalls += 1
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
        )

        val result = service.runDown(VERSION_ID, scope, registration)

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertIs<SQLException>(failure.exception)
        assertSame(failure.exception, scope.firstFailure)
        assertEquals(1, database.dmlCount)
        assertEquals(0, database.deleteTagCount)
    }

    @Test
    fun `failed tag deletion by id is returned and recorded in the scope`() {
        database.failDmlCalls += 2
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID),
            emptyList(),
        )

        val result = service.runDown(VERSION_ID, scope, registration)

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertIs<SQLException>(failure.exception)
        assertSame(failure.exception, scope.firstFailure)
        val tagDelete = database.dmlCalls.last()
        assertTrue(tagDelete.sql.contains("system_migration_tag", ignoreCase = true))
        assertTrue(tagDelete.sql.contains("id", ignoreCase = true))
        assertTrue(!tagDelete.sql.contains("tagName", ignoreCase = true))
        assertTrue(!tagDelete.sql.contains("tagType", ignoreCase = true))
        assertEquals(listOf(MIGRATION_TAG_ID), tagDelete.parameters)
        assertTrue(database.executedSql.none { it.contains("system_migration_version", ignoreCase = true) })
    }

    @Test
    fun `runDown preserves item tag when another version still references it`() {
        database.respond(
            rows(id = MIGRATION_TAG_ID, tagType = MIGRATION_TAG_TYPE),
            rows(id = APPLIED_ID, versionId = VERSION_ID, otherVersionId = OTHER_VERSION_ID),
            rows(id = OTHER_APPLIED_ID, versionId = OTHER_VERSION_ID),
        )

        val result = service.runDown(VERSION_ID, scope, registration)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(1, SampleMigration.downCalls)
        assertEquals(1, database.deleteHistoryCount)
        assertEquals(0, database.deleteTagCount)
    }

    @Test
    fun `runUp rejects non-positive version id without reading or executing`() {
        val result = service.runUp(0, scope, registration)

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(database.executedSql.isEmpty())
        assertEquals(0, SampleMigration.upCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `runDown rejects non-positive version id without reading or executing`() {
        val result = service.runDown(0, scope, registration)

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(database.executedSql.isEmpty())
        assertEquals(0, SampleMigration.downCalls)
        assertEquals(0, database.dmlCount)
    }

    @Test
    fun `transactionUp executes a Version in declaration order in one shared transaction`() {
        VersionMigrationTrace.reset()
        database.respond(
            emptyList(), // Version Tag lookup.
            emptyList(), emptyList(), emptyList(), // First Migration history checks.
            emptyList(), emptyList(), emptyList(), // Second Migration history checks.
        )

        val result = versionService(versionConfiguration(firstRegistration, secondRegistration))
            .transactionUp(VersionTag)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("up:first", "up:second"), VersionMigrationTrace.events)
        assertEquals(1, database.connectionBuildCount)
        assertEquals(1, database.autoCommitFalseCount)
        assertEquals(1, database.autoCommitRestoreCount)
        assertEquals(1, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(1, database.connectionCloseCount)
        assertTrue(database.autoCommitAtPrepareStatement.all { !it })
        assertEquals(listOf("autoCommit:false", "commit", "autoCommit:true", "close"), database.lifecycleEvents)
        assertEquals(1, database.dmlCalls.count {
            it.sql.contains("INSERT", true) && it.parameters == listOf(VersionTag.value, VERSION_TAG_TYPE)
        })
        assertTrue(database.executedSql.filter { it.contains("SELECT", true) }
            .none { it.contains("system_migration_version", true) })
        val versionInsert = database.dmlCalls.first { it.sql.contains("INSERT", true) &&
            it.sql.contains("system_migration_version", true) }
        val versionId = database.generatedIdForVersionInsert
        assertEquals(listOf(1000), versionInsert.parameters)
        val historyInserts = database.dmlCalls.filter { it.sql.contains("INSERT", true) &&
            it.sql.contains("version_id", true) }
        assertEquals(2, historyInserts.size)
        assertTrue(historyInserts.all { versionId.toInt() in it.parameters })
    }

    @Test
    fun `transactionUp rejects a persisted Version before running its migrations`() {
        VersionMigrationTrace.reset()
        database.respond(
            rows(id = 51, tagType = VERSION_TAG_TYPE),
            rows(id = VERSION_ID, tagId = 51),
        )

        val result = versionService(versionConfiguration(firstRegistration))
            .transactionUp(VersionTag)

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertTrue(failure.exception.message.orEmpty().contains("already applied"))
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.commitCount)
        assertEquals(1, database.rollbackCount)
    }

    @Test
    fun `transactionUp reports multiple persisted Versions for one Tag as inconsistent history`() {
        VersionMigrationTrace.reset()
        database.respond(
            rows(id = 51, tagType = VERSION_TAG_TYPE),
            listOf(
                mapOf("id" to VERSION_ID, "tagId" to 51),
                mapOf("id" to VERSION_ID + 1, "tagId" to 51),
            ),
        )

        val result = versionService(versionConfiguration(firstRegistration))
            .transactionUp(VersionTag)

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertTrue(failure.exception.message.orEmpty().contains("inconsistent"))
        assertTrue(failure.exception.message.orEmpty().contains("2 Versions"))
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.commitCount)
        assertEquals(1, database.rollbackCount)
        assertEquals(1, database.connectionCloseCount)
    }

    @Test
    fun `transactionUp stops on a middle Migration failure and rolls back the Version`() {
        VersionMigrationTrace.reset()
        database.failDdlCalls += 2
        database.respond(
            emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(),
            emptyList(),
        )

        val result = versionService(versionConfiguration(firstRegistration, secondRegistration, thirdRegistration))
            .transactionUp(VersionTag)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("up:first", "up:second"), VersionMigrationTrace.events)
        assertEquals(0, database.commitCount)
        assertEquals(1, database.rollbackCount)
        assertEquals(
            listOf("autoCommit:false", "rollback", "autoCommit:true", "close"),
            database.lifecycleEvents,
        )
        assertTrue(database.executedSql.none { it.contains("DELETE", true) &&
            it.contains("system_migration_version", true) })
    }

    @Test
    fun `transactionUp rejects an invalid generated Version id before running migrations`() {
        VersionMigrationTrace.reset()
        database.nextGeneratedId = Int.MAX_VALUE.toLong()
        database.respond(emptyList(), emptyList())

        val result = versionService(versionConfiguration(firstRegistration)).transactionUp(VersionTag)

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertTrue(database.generatedIdForVersionInsert > Int.MAX_VALUE.toLong())
        assertEquals(0, database.commitCount)
        assertEquals(1, database.rollbackCount)
    }

    @Test
    fun `transactionUp propagates migration lookup failure after creating a new Version`() {
        VersionMigrationTrace.reset()
        database.failReadCalls += 2
        database.respond(emptyList())

        val result = versionService(versionConfiguration(firstRegistration)).transactionUp(VersionTag)

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(2, database.dmlCount)
        assertTrue(database.dmlCalls.any { it.sql.contains("system_migration_tag", true) })
        assertTrue(database.dmlCalls.any { it.sql.contains("system_migration_version", true) })
        assertEquals(0, database.commitCount)
        assertEquals(1, database.rollbackCount)
    }

    @Test
    fun `transactionDown runs Migrations in reverse order and deletes the Version last`() {
        VersionMigrationTrace.reset()
        database.respond(
            tagRows(
                tag(51, VersionTag.value, VERSION_TAG_TYPE),
                tag(52, VersionItemSecond.value, MIGRATION_TAG_TYPE),
                tag(53, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            ),
            rows(id = VERSION_ID, tagId = 51),
            historyRows(history(61, 53, VERSION_ID), history(62, 52, VERSION_ID)),
            rows(id = 52, tagType = MIGRATION_TAG_TYPE), rows(id = 62, versionId = VERSION_ID), emptyList(),
            rows(id = 53, tagType = MIGRATION_TAG_TYPE), rows(id = 61, versionId = VERSION_ID), emptyList(),
        )

        val result = versionService(versionConfiguration(firstRegistration, secondRegistration))
            .transactionDown(VersionTag)

        assertEquals(
            true,
            assertIs<ExecuteResult.Success<Boolean>>(
                result,
                (result as? ExecuteResult.Failure)?.exception?.message,
            ).result,
        )
        assertEquals(listOf("down:second", "down:first"), VersionMigrationTrace.events)
        assertEquals(1, database.connectionBuildCount)
        assertEquals(1, database.autoCommitFalseCount)
        assertEquals(1, database.autoCommitRestoreCount)
        assertEquals(1, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertTrue(database.autoCommitAtPrepareStatement.all { !it })
        val historyDeletes = database.dmlCalls.filter { it.sql.contains("DELETE", true) &&
            it.sql.contains("version_id", true) }
        assertEquals(2, historyDeletes.size)
        assertTrue(historyDeletes.all { VERSION_ID in it.parameters })
        val versionDelete = database.dmlCalls.last()
        assertTrue(versionDelete.sql.contains("DELETE", true))
        assertTrue(versionDelete.sql.contains("system_migration_version", true))
        assertTrue(versionDelete.sql.contains("id", true))
        assertTrue(!versionDelete.sql.contains("tag_id", true))
        assertEquals(listOf(VERSION_ID), versionDelete.parameters)
    }

    @Test
    fun `transactionDown stops after a middle Migration failure and retains Version record`() {
        VersionMigrationTrace.reset()
        database.failDdlCalls += 2
        database.respond(
            tagRows(
                tag(51, VersionTag.value, VERSION_TAG_TYPE),
                tag(52, VersionItemFirst.value, MIGRATION_TAG_TYPE),
                tag(53, VersionItemSecond.value, MIGRATION_TAG_TYPE),
                tag(54, VersionItemThird.value, MIGRATION_TAG_TYPE),
            ),
            rows(id = VERSION_ID, tagId = 51),
            historyRows(
                history(61, 52, VERSION_ID),
                history(62, 53, VERSION_ID),
                history(63, 54, VERSION_ID),
            ),
            rows(id = 54, tagType = MIGRATION_TAG_TYPE), rows(id = 63, versionId = VERSION_ID), emptyList(),
            rows(id = 53, tagType = MIGRATION_TAG_TYPE), rows(id = 62, versionId = VERSION_ID),
        )

        val result = versionService(versionConfiguration(firstRegistration, secondRegistration, thirdRegistration))
            .transactionDown(VersionTag)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("down:third", "down:second"), VersionMigrationTrace.events)
        assertTrue(database.dmlCalls.none { it.sql.contains("DELETE", true) &&
            it.sql.contains("system_migration_version", true) })
        assertEquals(0, database.commitCount)
        assertEquals(1, database.rollbackCount)
    }

    @Test
    fun `transactionDown rejects a Version without persisted history`() {
        VersionMigrationTrace.reset()
        database.respond(
            tagRows(tag(51, VersionTag.value, VERSION_TAG_TYPE)),
            emptyList(),
            emptyList(),
        )

        val result = versionService(versionConfiguration(firstRegistration)).transactionDown(VersionTag)

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.commitCount)
        assertEquals(1, database.rollbackCount)
    }

    @Test
    fun `transactionDown rolls back when deleting anything other than exactly one Version`() {
        VersionMigrationTrace.reset()
        database.respondUpdateCounts(1, 1, 0)
        database.respond(
            tagRows(
                tag(51, VersionTag.value, VERSION_TAG_TYPE),
                tag(52, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            ),
            rows(id = VERSION_ID, tagId = 51),
            historyRows(history(61, 52, VERSION_ID)),
            rows(id = 52, tagType = MIGRATION_TAG_TYPE), rows(id = 61, versionId = VERSION_ID), emptyList(),
        )

        val result = versionService(versionConfiguration(firstRegistration)).transactionDown(VersionTag)

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("down:first"), VersionMigrationTrace.events)
        assertEquals(0, database.commitCount)
        assertEquals(1, database.rollbackCount)
        assertTrue(database.dmlCalls.last().sql.contains("system_migration_version", true))
        assertEquals(listOf(VERSION_ID), database.dmlCalls.last().parameters)
    }

    @Test
    fun `transactionUp reuses an existing Version Tag when no Version is applied`() {
        VersionMigrationTrace.reset()
        database.respond(
            versionTagRow(id = 51),
            emptyList(), // The existing Tag has no persisted Version.
            emptyList(), emptyList(), emptyList(), // Migration history checks.
        )

        val result = versionService(versionConfiguration(firstRegistration)).transactionUp(VersionTag)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("up:first"), VersionMigrationTrace.events)
        assertEquals(1, database.dmlCalls.count {
            it.sql.contains("INSERT", true) && it.sql.contains("system_migration_version", true)
        })
        assertTrue(database.dmlCalls.none {
            it.sql.contains("INSERT", true) && it.sql.contains("system_migration_tag", true) &&
                it.parameters.firstOrNull() == VersionTag.value
        })
        assertEquals(1, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `transactionDown validates the complete history before running down`() {
        val cases = listOf(
            Triple("incomplete", listOf(history(61, 52, VERSION_ID)), listOf(
                tag(52, VersionItemFirst.value, MIGRATION_TAG_TYPE),
                tag(53, VersionItemSecond.value, MIGRATION_TAG_TYPE),
            )),
            Triple("extra", listOf(
                history(61, 52, VERSION_ID),
                history(62, 54, VERSION_ID),
            ), listOf(
                tag(52, VersionItemFirst.value, MIGRATION_TAG_TYPE),
                tag(54, "unconfigured_migration", MIGRATION_TAG_TYPE),
            )),
            Triple("duplicate", listOf(
                history(61, 52, VERSION_ID),
                history(62, 52, VERSION_ID),
            ), listOf(
                tag(52, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            )),
            Triple("wrong version", listOf(history(61, 52, OTHER_VERSION_ID)), listOf(
                tag(52, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            )),
        )

        cases.forEach { (caseName, historyRecords, tagRecords) ->
            setUp()
            VersionMigrationTrace.reset()
            val configuration = if (caseName == "incomplete") {
                versionConfiguration(firstRegistration, secondRegistration)
            } else {
                versionConfiguration(firstRegistration)
            }
            val tags = buildList {
                add(tag(51, VersionTag.value, VERSION_TAG_TYPE))
                addAll(tagRecords)
                if (caseName == "wrong version") add(tag(55, "other_version", VERSION_TAG_TYPE))
            }
            val versions = if (caseName == "wrong version") {
                listOf(mapOf("id" to VERSION_ID, "tagId" to 51), mapOf("id" to OTHER_VERSION_ID, "tagId" to 55))
            } else {
                listOf(mapOf("id" to VERSION_ID, "tagId" to 51))
            }
            database.respond(
                tagRows(*tags.toTypedArray()),
                versions,
                historyRows(*historyRecords.toTypedArray()),
            )
            val result = versionService(configuration).transactionDown(VersionTag)

            val failure = assertIs<ExecuteResult.Failure>(result, "$caseName: ${(result as? ExecuteResult.Failure)?.exception?.message}")
            val expectedFailureText = when (caseName) {
                "incomplete" -> "history rows"
                "extra" -> "not present in the configuration"
                "duplicate" -> "Duplicate applied Migration history"
                else -> "different persisted Version"
            }
            assertTrue(
                failure.exception.message.orEmpty().contains(expectedFailureText, ignoreCase = true),
                "$caseName: ${failure.exception.message}",
            )
            assertTrue(VersionMigrationTrace.events.isEmpty(), caseName)
            assertTrue(database.dmlCalls.isEmpty(), caseName)
            assertEquals(0, database.commitCount, caseName)
            assertEquals(1, database.rollbackCount, caseName)
        }
    }

    @Test
    fun `transactionDown retains Version Tag and allows applying that tag again after rollback`() {
        VersionMigrationTrace.reset()
        database.respond(
            // Rollback reads a complete snapshot, then runDown reads the selected Migration rows.
            tagRows(
                tag(51, VersionTag.value, VERSION_TAG_TYPE),
                tag(52, VersionItemFirst.value, MIGRATION_TAG_TYPE),
            ),
            rows(id = VERSION_ID, tagId = 51),
            historyRows(history(61, 52, VERSION_ID)),
            rows(id = 52, tagType = MIGRATION_TAG_TYPE),
            rows(id = 61, versionId = VERSION_ID),
            emptyList(),
            // Reapply under the retained Version Tag. No Version row is currently applied.
            versionTagRow(id = 51),
            emptyList(),
            emptyList(), emptyList(), emptyList(),
        )

        val service = versionService(versionConfiguration(firstRegistration))
        val down = service.transactionDown(VersionTag)
        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(down).result)

        val up = service.transactionUp(VersionTag)

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(up).result)
        assertEquals(listOf("down:first", "up:first"), VersionMigrationTrace.events)
        assertEquals(2, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(2, database.dmlCalls.count {
            it.sql.contains("system_migration_version", true) && it.sql.contains("DELETE", true)
        } + database.dmlCalls.count {
            it.sql.contains("system_migration_version", true) && it.sql.contains("INSERT", true)
        })
        assertTrue(database.dmlCalls.none {
            it.sql.contains("INSERT", true) && it.sql.contains("system_migration_tag", true) &&
                it.parameters.firstOrNull() == VersionTag.value
        })
        val versionDelete = database.dmlCalls.first { it.sql.contains("DELETE", true) &&
            it.sql.contains("system_migration_version", true) }
        assertEquals(listOf(VERSION_ID), versionDelete.parameters)
    }

    @Test
    fun `transaction preparation failure is reported before starting a child transaction`() {
        VersionMigrationTrace.reset()

        val result = versionService(versionConfiguration(firstRegistration, versionName = "another_version"))
            .transactionUp(VersionTag)

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(database.executedSql.isEmpty())
        assertEquals(1, database.connectionBuildCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(0, database.autoCommitFalseCount)
        assertEquals(1, database.connectionCloseCount)
    }

    @Test
    fun `migrate runs all pending Versions in ascending order with one transaction each`() {
        VersionMigrationTrace.reset()
        database.respond(*emptyReadResponses())
        val orchestration = MigrationController(orchestrationConfiguration())

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            orchestration.migrate(scope)
        }

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(
            listOf("up:first", "up:second", "up:third", "up:fourth", "up:fifth"),
            VersionMigrationTrace.events,
        )
        assertEquals(4, database.commitCount) // Includes the configured empty Version.
        assertEquals(0, database.rollbackCount)
        assertEquals(4, database.dmlCalls.count { it.sql.contains("system_migration_version", true) })
        assertEquals(1, database.connectionBuildCount)
        assertEquals(4, database.autoCommitFalseCount)
        assertEquals(4, database.autoCommitRestoreCount)
        assertEquals(1, database.connectionCloseCount)
        assertEquals(1, database.events.count { it == "lock-acquire" })
        assertEquals(1, database.events.count { it == "lock-release" })
        val events = database.events
        val firstRead = events.indexOfFirst { it.startsWith("read:") }
        val lastCommit = events.indexOfLast { it == "commit" }
        assertTrue(events.indexOf("lock-acquire") < firstRead)
        assertTrue(lastCommit < events.indexOf("lock-release"))
        assertTrue(events.indexOf("lock-release") < events.indexOf("connection-close"))
    }

    @Test
    fun `migrate to Version includes the target and executes no later Version`() {
        VersionMigrationTrace.reset()
        database.respond(*emptyReadResponses())
        val orchestration = MigrationController(orchestrationConfiguration())

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            orchestration.migrate(VersionTagTwo, scope)
        }

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("up:first", "up:second", "up:third"), VersionMigrationTrace.events)
        assertEquals(2, database.commitCount)
        assertEquals(2, database.dmlCalls.count { it.sql.contains("system_migration_version", true) })
        assertEquals(1, database.connectionBuildCount)
    }

    @Test
    fun `all four orchestration commands fail closed without identity before reading History`() {
        val target = object : MigrationVersionTag { override val value = "unknown_target" }
        val controller = MigrationController(MigrationConfiguration(emptyList()))
        val commands: List<(OperationExecutionScope) -> ExecuteResult<Boolean>> = listOf(
            controller::migrate,
            { scope -> controller.migrate(target, scope) },
            controller::rollback,
            { scope -> controller.rollback(target, scope) },
        )

        commands.forEach { command ->
            database = FakeDatabase()
            val result = OperationExecutionExecutor(versionTestContext(withIdentity = false)).connection(command)

            val failure = assertIs<ExecuteResult.Failure>(result).exception
            assertTrue(failure.message.orEmpty().contains("DatabaseIdentity"))
            assertTrue(database.events.none { it.startsWith("read:") || it == "lock-acquire" })
            assertEquals(0, database.commitCount)
            assertEquals(0, database.rollbackCount)
        }
    }

    @Test
    fun `lock timeout prevents History reads and does not attempt release`() {
        database.systemLockAcquireValue = 0

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(MigrationConfiguration(emptyList())).migrate(scope)
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("Timed out acquiring session advisory lock"))
        assertEquals(listOf("lock-acquire", "connection-close"), database.events)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `acquire SQL failure prevents History reads and does not attempt release`() {
        val acquireFailure = SQLException("GET_LOCK failed")
        database.systemLockAcquireFailure = acquireFailure

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(MigrationConfiguration(emptyList())).migrate(scope)
        }

        assertSame(acquireFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("lock-acquire", "connection-close"), database.events)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `lock release failure is returned after the full orchestration callback`() {
        database.systemLockReleaseValue = 0
        database.respond(*emptyReadResponses())

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(MigrationConfiguration(emptyList())).migrate(scope)
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("RELEASE_LOCK did not own"))
        assertEquals(3, database.events.count { it.startsWith("read:") })
        assertTrue(database.events.indexOf("lock-release") < database.events.indexOf("connection-close"))
    }

    @Test
    fun `callback failure remains primary when orchestration lock release fails`() {
        database.systemLockReleaseValue = 0
        database.respond(*emptyReadResponses())
        val configuration = MigrationConfiguration(
            listOf(MigrationVersion("tagless_version", listOf(SingleMigration(firstRegistration)))),
        )

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(configuration).migrate(scope)
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("no valid MigrationVersionTag"))
        assertEquals(1, failure.suppressed.size)
        assertTrue(failure.suppressed.single().message.orEmpty().contains("RELEASE_LOCK did not own"))
        assertTrue(database.events.indexOf("lock-release") < database.events.indexOf("connection-close"))
    }

    @Test
    fun `fatal Migration Error releases system lock before Operation closes Connection`() {
        val fatal = AssertionError("fatal migration callback")
        SampleMigration.upFailure = fatal
        database.respond(*emptyReadResponses())
        val configuration = migrationConfig {
            migrationVersion(VersionTagOne) { migration<SampleMigration>() }
        }

        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor(versionTestContext()).connection { scope ->
                MigrationController(configuration).migrate(scope)
            }
        }

        assertSame(fatal, thrown)
        assertEquals(1, database.rollbackCount)
        assertTrue(database.events.indexOf("lock-acquire") < database.events.indexOf("lock-release"))
        assertTrue(database.events.indexOf("lock-release") < database.events.indexOf("connection-close"))
    }

    @Test
    fun `rollback runs only latest applied Version and reverses Migration declaration order`() {
        VersionMigrationTrace.reset()
        val history = orchestrationHistory(appliedVersionCount = 3)
        respondForRollback(history, listOf(VersionTagThree.value))
        val orchestration = MigrationController(orchestrationConfiguration())

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            orchestration.rollback(scope)
        }

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("down:fifth", "down:fourth"), VersionMigrationTrace.events)
        assertEquals(1, database.commitCount)
        assertEquals(0, database.rollbackCount)
        val versionDeletes = database.dmlCalls.filter {
            it.sql.contains("DELETE", true) && it.sql.contains("system_migration_version", true)
        }
        assertEquals(1, versionDeletes.size)
        assertEquals(listOf(VERSION_ID_THREE), versionDeletes.map { it.parameters.single() })
        assertEquals(1, database.connectionBuildCount)
    }

    @Test
    fun `rollback to target excludes target and traverses applied Versions descending`() {
        VersionMigrationTrace.reset()
        val history = orchestrationHistory(appliedVersionCount = 3)
        respondForRollback(history, listOf(VersionTagThree.value, VersionTagTwo.value))
        val orchestration = MigrationController(orchestrationConfiguration())

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            orchestration.rollback(VersionTagOne, scope)
        }

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("down:fifth", "down:fourth", "down:third"), VersionMigrationTrace.events)
        assertEquals(2, database.commitCount)
        val versionDeletes = database.dmlCalls.filter {
            it.sql.contains("DELETE", true) && it.sql.contains("system_migration_version", true)
        }
        assertEquals(2, versionDeletes.size)
        assertEquals(listOf(VERSION_ID_THREE, VERSION_ID_TWO), versionDeletes.map { it.parameters.single() })
        assertEquals(1, database.connectionBuildCount)
    }

    @Test
    fun `failed later Version rolls back only itself and keeps earlier Version committed`() {
        VersionMigrationTrace.reset()
        database.failDdlCalls += 2
        database.respond(*emptyReadResponses())
        val configuration = migrationConfig {
            migrationVersion(VersionTagOne) { migration<VersionFirstMigration>() }
            migrationVersion(VersionTagTwo) { migration<VersionThirdMigration>() }
        }

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(configuration).migrate(scope)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("up:first", "up:third"), VersionMigrationTrace.events)
        assertEquals(1, database.commitCount)
        assertEquals(1, database.rollbackCount)
        assertEquals(1, database.connectionBuildCount)
        assertEquals(2, database.autoCommitFalseCount)
        assertEquals(2, database.autoCommitRestoreCount)
    }

    @Test
    fun `unknown target and invalid history fail before any Version executes`() {
        VersionMigrationTrace.reset()
        database.respond(*emptyReadResponses())
        val orchestration = MigrationController(orchestrationConfiguration())
        val unknownTarget = object : MigrationVersionTag { override val value = "not_registered" }

        val unknownResult = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            orchestration.migrate(unknownTarget, scope)
        }

        assertIs<ExecuteResult.Failure>(unknownResult)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)

        val unknownRollbackResult = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            orchestration.rollback(unknownTarget, scope)
        }
        assertIs<ExecuteResult.Failure>(unknownRollbackResult)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)

        setUp()
        val unknownPersistedTag = mapOf("id" to 900, "tagName" to "removed_version", "tagType" to VERSION_TAG_TYPE)
        database.respond(
            listOf(unknownPersistedTag),
            listOf(mapOf("id" to 901, "tagId" to 900)),
            emptyList(),
        )
        val invalidHistoryResult = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(orchestrationConfiguration()).migrate(scope)
        }

        assertIs<ExecuteResult.Failure>(invalidHistoryResult)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)

        database.respond(
            listOf(unknownPersistedTag),
            listOf(mapOf("id" to 901, "tagId" to 900)),
            emptyList(),
        )
        val invalidHistoryRollbackResult = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(orchestrationConfiguration()).rollback(scope)
        }
        assertIs<ExecuteResult.Failure>(invalidHistoryRollbackResult)
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `unsupported DOWN fails the Version transaction instead of skipping it`() {
        VersionMigrationTrace.reset()
        val configuration = migrationConfig {
            migrationVersion(VersionTagOne) { migration<UnsupportedDownMigration>() }
        }
        val tag = mapOf("id" to 51, "tagName" to VersionTagOne.value, "tagType" to VERSION_TAG_TYPE)
        val item = mapOf("id" to 71, "tagName" to UnsupportedDownItem.value, "tagType" to MIGRATION_TAG_TYPE)
        val version = mapOf("id" to 101, "tagId" to 51)
        val applied = mapOf("id" to 801, "tagId" to 71, "versionId" to 101)
        database.respond(
            listOf(tag, item), listOf(version), listOf(applied), // Current-version validation.
            listOf(tag, item), listOf(version), listOf(applied), // transactionDown history validation.
            listOf(mapOf("id" to 71, "tagType" to MIGRATION_TAG_TYPE)),
            listOf(mapOf("id" to 801, "versionId" to 101)),
        )

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(configuration).rollback(scope)
        }

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertIs<UnsupportedOperationException>(failure.exception)
        assertEquals(listOf("down:unsupported"), VersionMigrationTrace.events)
        assertEquals(0, database.commitCount)
        assertEquals(1, database.rollbackCount)
        assertTrue(database.dmlCalls.none { it.sql.contains("DELETE", true) })
    }

    @Test
    fun `missing selected Version tag fails before starting any Version transaction`() {
        VersionMigrationTrace.reset()
        database.respond(*emptyReadResponses())
        val configuration = MigrationConfiguration(
            listOf(MigrationVersion("tagless_version", listOf(SingleMigration(firstRegistration)))),
        )

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(configuration).migrate(scope)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertTrue(assertIs<ExecuteResult.Failure>(result).exception.message.orEmpty().contains("no valid MigrationVersionTag"))
        assertTrue(VersionMigrationTrace.events.isEmpty())
        assertEquals(0, database.dmlCount)
        assertEquals(0, database.autoCommitFalseCount)
        assertEquals(0, database.commitCount)
        assertEquals(0, database.rollbackCount)
    }

    @Test
    fun `repeated migrate and rollback are idempotent for an applied Version`() {
        VersionMigrationTrace.reset()
        val history = singleMigrationHistory()
        database.respond(
            emptyList(), emptyList(), emptyList(), // Initial history.
            emptyList(), emptyList(), emptyList(), emptyList(), // Initial Version up.
            history.tags, history.versions, history.migrations, // Repeated migrate preflight.
            history.tags, history.versions, history.migrations, // Rollback preflight.
            history.tags, history.versions, history.migrations, // Version transactionDown validation.
            listOf(mapOf("id" to ITEM_TAG_ID_FIRST, "tagType" to MIGRATION_TAG_TYPE)),
            listOf(mapOf("id" to 800, "versionId" to VERSION_ID_ONE)),
            emptyList(),
            history.tags.take(1), emptyList(), emptyList(), // Repeated rollback preflight after rollback.
        )
        val orchestration = MigrationController(
            migrationConfig { migrationVersion(VersionTagOne) { migration<VersionFirstMigration>() } },
        )

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(orchestration.migrate(scope)).result)
            assertEquals(4, database.dmlCount)
            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(orchestration.migrate(scope)).result)
            assertEquals(4, database.dmlCount)
            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(orchestration.rollback(scope)).result)
            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(orchestration.rollback(scope)).result)
            ExecuteResult.Success(true)
        }

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(listOf("up:first", "down:first"), VersionMigrationTrace.events)
        assertEquals(2, database.commitCount)
        assertEquals(0, database.rollbackCount)
        assertEquals(1, database.connectionBuildCount)
    }

    @Test
    fun `empty Version is persisted and committed instead of silently skipped`() {
        database.respond(*emptyReadResponses())
        val configuration = migrationConfig { migrationVersion(VersionTagOne) { } }

        val result = OperationExecutionExecutor(versionTestContext()).connection { scope ->
            MigrationController(configuration).migrate(scope)
        }

        assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(result).result)
        assertEquals(1, database.commitCount)
        assertEquals(1, database.dmlCalls.count { it.sql.contains("system_migration_version", true) })
        assertEquals(0, database.executedSql.count { it.contains("CREATE TABLE", true) })
    }

    private fun orchestrationConfiguration() = migrationConfig {
        migrationVersion(VersionTagOne) {
            migration<VersionFirstMigration>()
            migration<VersionSecondMigration>()
        }
        migrationVersion(VersionTagTwo) { migration<VersionThirdMigration>() }
        migrationVersion(VersionTagThree) {
            migration<VersionFourthMigration>()
            migration<VersionFifthMigration>()
        }
        migrationVersion(VersionTagFour) { }
    }

    private fun emptyReadResponses(count: Int = 100): Array<List<Map<String, Any?>>> =
        Array(count) { emptyList() }

    private fun orchestrationHistory(appliedVersionCount: Int): OrchestrationHistory {
        val definitions = listOf(
            Triple(VersionTagOne, VERSION_TAG_ID_ONE, VERSION_ID_ONE) to listOf(
                VersionItemFirst.value to ITEM_TAG_ID_FIRST,
                VersionItemSecond.value to ITEM_TAG_ID_SECOND,
            ),
            Triple(VersionTagTwo, VERSION_TAG_ID_TWO, VERSION_ID_TWO) to listOf(
                VersionItemThird.value to ITEM_TAG_ID_THIRD,
            ),
            Triple(VersionTagThree, VERSION_TAG_ID_THREE, VERSION_ID_THREE) to listOf(
                VersionItemFourth.value to ITEM_TAG_ID_FOURTH,
                VersionItemFifth.value to ITEM_TAG_ID_FIFTH,
            ),
            Triple(VersionTagFour, VERSION_TAG_ID_FOUR, VERSION_ID_FOUR) to emptyList(),
        )
        val tags = definitions.map { (version, _) ->
            mapOf("id" to version.second, "tagName" to version.first.value, "tagType" to VERSION_TAG_TYPE)
        } + definitions.take(appliedVersionCount).flatMap { (_, migrations) ->
            migrations.map { (name, id) ->
                mapOf("id" to id, "tagName" to name, "tagType" to MIGRATION_TAG_TYPE)
            }
        }
        val versions = definitions.take(appliedVersionCount).map { (version, _) ->
            mapOf("id" to version.third, "tagId" to version.second)
        }
        var historyId = 700
        val migrations = definitions.take(appliedVersionCount).flatMap { (version, items) ->
            items.map { (_, itemTagId) ->
                mapOf("id" to historyId++, "tagId" to itemTagId, "versionId" to version.third)
            }
        }
        return OrchestrationHistory(tags, versions, migrations)
    }

    private fun singleMigrationHistory(): OrchestrationHistory = OrchestrationHistory(
        tags = listOf(
            mapOf("id" to VERSION_TAG_ID_ONE, "tagName" to VersionTagOne.value, "tagType" to VERSION_TAG_TYPE),
            mapOf("id" to ITEM_TAG_ID_FIRST, "tagName" to VersionItemFirst.value, "tagType" to MIGRATION_TAG_TYPE),
        ),
        versions = listOf(mapOf("id" to VERSION_ID_ONE, "tagId" to VERSION_TAG_ID_ONE)),
        migrations = listOf(mapOf("id" to 700, "tagId" to ITEM_TAG_ID_FIRST, "versionId" to VERSION_ID_ONE)),
    )

    /** Queues the initial preflight and each child transaction's reads in their execution order. */
    private fun respondForRollback(history: OrchestrationHistory, descendingVersionNames: List<String>) {
        fun respondSnapshot() = database.respond(history.tags, history.versions, history.migrations)
        respondSnapshot() // Preflight validates complete history before the first rollback.
        val migrationIdsByVersion = mapOf(
            VersionTagOne.value to listOf(VersionItemFirst.value to ITEM_TAG_ID_FIRST, VersionItemSecond.value to ITEM_TAG_ID_SECOND),
            VersionTagTwo.value to listOf(VersionItemThird.value to ITEM_TAG_ID_THIRD),
            VersionTagThree.value to listOf(VersionItemFourth.value to ITEM_TAG_ID_FOURTH, VersionItemFifth.value to ITEM_TAG_ID_FIFTH),
            VersionTagFour.value to emptyList(),
        )
        descendingVersionNames.forEach { versionName ->
            respondSnapshot() // Per-Version transaction validation.
            val versionId = history.versions.first { it["tagId"] == when (versionName) {
                VersionTagOne.value -> VERSION_TAG_ID_ONE
                VersionTagTwo.value -> VERSION_TAG_ID_TWO
                VersionTagThree.value -> VERSION_TAG_ID_THREE
                else -> VERSION_TAG_ID_FOUR
            } }["id"]
            migrationIdsByVersion.getValue(versionName).asReversed().forEach { (name, tagId) ->
                val tagName = history.tags.first { it["id"] == tagId }["tagName"]
                database.respond(
                    listOf(mapOf("id" to tagId, "tagType" to MIGRATION_TAG_TYPE)),
                    history.migrations.filter { it["tagId"] == tagId && it["versionId"] == versionId }
                        .map { mapOf("id" to it["id"], "versionId" to it["versionId"]) },
                    emptyList(), // No history remains after deleting this item.
                )
                assertEquals(name, tagName)
            }
        }
    }

    private data class OrchestrationHistory(
        val tags: List<Map<String, Any?>>,
        val versions: List<Map<String, Any?>>,
        val migrations: List<Map<String, Any?>>,
    )

    @Test
    fun `version service failure remains latched when caller ignores its returned result`() {
        val context = versionTestContext()
        database.failDdlCalls += 1
        database.respond(emptyList(), emptyList())
        val service = MigrationVersionTransactionService(versionConfiguration(firstRegistration))

        val outerResult = OperationExecutionExecutor(context).connection { scope ->
            assertIs<ExecuteResult.Failure>(service.transactionUp(scope, VersionTag))
            val statementCountAfterFailure = database.executedSql.size
            assertIs<ExecuteResult.Failure>(service.transactionUp(scope, VersionTag))
            assertEquals(statementCountAfterFailure, database.executedSql.size)
            ExecuteResult.Success(true)
        }

        assertIs<ExecuteResult.Failure>(outerResult)
        assertEquals(1, database.connectionBuildCount)
        assertEquals(0, database.commitCount)
        assertEquals(1, database.rollbackCount)
        assertEquals(1, database.connectionCloseCount)
    }

    private fun versionService(configuration: MigrationConfiguration): VersionTransactionHarness {
        return VersionTransactionHarness(
            MigrationVersionTransactionService(configuration),
            OperationExecutionExecutor(versionTestContext()),
        )
    }

    private fun versionTestContext(withIdentity: Boolean = true): DatabaseContext {
        val config = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = "jdbc:mysql://migration-test.local",
                dbPort = 3306,
                dbName = "migration_test",
                dbUserName = "test-user",
                dbPassword = "test-password",
            ),
            platform = DatabasePlatformConfig(DialectQuery.MY_SQL),
        )
        val identity = if (withIdentity) DatabaseIdentity.from(config) else null
        return DatabaseContext(
            "migration-version-test",
            config,
            object : IDatabaseConnection {
                override fun build(): Connection {
                    database.connectionBuildCount++
                    return database.connection
                }
            },
            identity,
        )
    }

    /** Parent owns the Connection; each Migration Version owns one child transaction. */
    private class VersionTransactionHarness(
        private val service: MigrationVersionTransactionService,
        private val callerExecutor: OperationExecutionExecutor,
    ) {
        fun transactionUp(versionTag: MigrationVersionTag): ExecuteResult<Boolean> =
            callerExecutor.connection { scope -> service.transactionUp(scope, versionTag) }

        fun transactionDown(versionTag: MigrationVersionTag): ExecuteResult<Boolean> =
            callerExecutor.connection { scope -> service.transactionDown(scope, versionTag) }
    }

    private fun versionConfiguration(
        vararg migrations: RegisteredMigration,
        versionName: String = VersionTag.validatedValue,
    ) = MigrationConfiguration(
        listOf(MigrationVersion(versionName, migrations.map(::SingleMigration))),
    )

    private val firstRegistration = registered(VersionItemFirst, VersionFirstMigration::class)
    private val secondRegistration = registered(VersionItemSecond, VersionSecondMigration::class)
    private val thirdRegistration = registered(VersionItemThird, VersionThirdMigration::class)

    private fun registered(tag: MigrationItemTag, type: kotlin.reflect.KClass<out Migration>) =
        RegisteredMigration(MigrationIdentity.fromValue(tag.value), type)

    private fun rows(
        id: Int,
        tagId: Int? = null,
        tagType: Int? = null,
        versionId: Int? = null,
        otherVersionId: Int? = null,
    ): List<Map<String, Any?>> = listOfNotNull(
        mapOfNotNull(
            "id" to id,
            "tagId" to tagId,
            "tagType" to tagType,
            "versionId" to versionId,
        ),
        otherVersionId?.let { mapOf("id" to OTHER_APPLIED_ID, "versionId" to it) },
    )

    private fun versionTagRow(id: Int): List<Map<String, Any?>> =
        listOf(tag(id, VersionTag.value, VERSION_TAG_TYPE))

    private fun tagRows(vararg tags: Map<String, Any?>): List<Map<String, Any?>> = tags.toList()

    private fun tag(id: Int, name: String, type: Int): Map<String, Any?> =
        mapOf("id" to id, "tagName" to name, "tagType" to type)

    private fun historyRows(vararg records: Map<String, Any?>): List<Map<String, Any?>> = records.toList()

    private fun history(id: Int, tagId: Int, versionId: Int): Map<String, Any?> =
        mapOf("id" to id, "tagId" to tagId, "versionId" to versionId)

    private fun mapOfNotNull(vararg values: Pair<String, Any?>): Map<String, Any?> =
        values.filter { it.second != null }.associate { it.first to it.second }

    private class FakeDatabase {
        val events = mutableListOf<String>()
        val executedSql = mutableListOf<String>()
        val failDmlCalls = mutableSetOf<Int>()
        val failDdlCalls = mutableSetOf<Int>()
        private val readResponses = ArrayDeque<List<Map<String, Any?>>>()
        private val updateCounts = ArrayDeque<Int>()
        var nextGeneratedId = 1000L
        val failReadCalls = mutableSetOf<Int>()
        private var readCount = 0
        private var lastGeneratedId = 0L
        private var ddlCount = 0

        val readParameters = mutableListOf<List<Any?>>()
        val dmlCalls = mutableListOf<DmlCall>()
        var connectionBuildCount = 0
        private var connectionAutoCommit = true
        var autoCommitFalseCount = 0
            private set
        var autoCommitRestoreCount = 0
            private set
        val autoCommitAtPrepareStatement = mutableListOf<Boolean>()
        val lifecycleEvents = mutableListOf<String>()
        var generatedIdForVersionInsert: Long = 0
            private set

        var dmlCount = 0
            private set
        var insertHistoryCount = 0
            private set
        var deleteCount = 0
            private set
        var deleteHistoryCount = 0
            private set
        var deleteTagCount = 0
            private set
        var connectionPreparationCount = 0
            private set
        var failDdl = false
        var systemLockAcquireValue: Int? = 1
        var systemLockReleaseValue: Int? = 1
        var systemLockAcquireFailure: SQLException? = null
        var systemLockReleaseFailure: SQLException? = null
        var statementCloseCount = 0
            private set
        var resultSetCloseCount = 0
            private set
        var connectionCloseCount = 0
            private set
        var commitCount = 0
            private set
        var rollbackCount = 0
            private set

        val connection: Connection = proxy(Connection::class.java) { method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    connectionPreparationCount++
                    autoCommitAtPrepareStatement += connectionAutoCommit
                    val sql = args[0] as String
                    executedSql += sql
                    preparedStatement(sql)
                }
                "setAutoCommit" -> {
                    connectionAutoCommit = args[0] as Boolean
                    if (!connectionAutoCommit) {
                        autoCommitFalseCount++
                        lifecycleEvents += "autoCommit:false"
                    } else {
                        autoCommitRestoreCount++
                        lifecycleEvents += "autoCommit:true"
                    }
                    null
                }
                "getAutoCommit" -> connectionAutoCommit
                "commit" -> { commitCount++; lifecycleEvents += "commit"; events += "commit"; null }
                "rollback" -> { rollbackCount++; lifecycleEvents += "rollback"; events += "rollback"; null }
                "close" -> { connectionCloseCount++; lifecycleEvents += "close"; events += "connection-close"; null }
                "isClosed" -> false
                "toString" -> "Fake Migration Connection"
                else -> primitiveDefault(method.returnType)
            }
        }

        fun respond(vararg results: List<Map<String, Any?>>) {
            results.forEach(readResponses::addLast)
        }

        fun respondUpdateCounts(vararg counts: Int) {
            counts.forEach(updateCounts::addLast)
        }

        private fun preparedStatement(sql: String): PreparedStatement {
            val values = sortedMapOf<Int, Any?>()
            return proxy(PreparedStatement::class.java) { method, args ->
                when (method.name) {
                    "setObject", "setString", "setInt", "setLong", "setNull" -> {
                        values[args[0] as Int] = if (method.name == "setNull") null else args.getOrNull(1)
                        null
                    }
                    "executeQuery" -> {
                        val normalizedSql = sql.lowercase()
                        if ("get_lock(" in normalizedSql) {
                            events += "lock-acquire"
                            systemLockAcquireFailure?.let { throw it }
                            return@proxy resultSet(listOf(mapOf("acquired" to systemLockAcquireValue)), sql)
                        }
                        if ("release_lock(" in normalizedSql) {
                            events += "lock-release"
                            systemLockReleaseFailure?.let { throw it }
                            return@proxy resultSet(listOf(mapOf("released" to systemLockReleaseValue)), sql)
                        }
                        readCount++
                        events += "read:$sql"
                        readParameters += values.values.toList()
                        if (readCount in failReadCalls) throw SQLException("Read $readCount failed")
                        resultSet(
                            if (readResponses.isEmpty()) emptyList() else readResponses.removeFirst(),
                            sql,
                        )
                    }
                    "executeUpdate" -> executeUpdate(sql, values.values.toList())
                    "execute" -> {
                        events += "ddl:$sql"
                        ddlCount++
                        if (failDdl || ddlCount in failDdlCalls) throw SQLException("DDL $ddlCount failed")
                        true
                    }
                    "getGeneratedKeys" -> resultSet(listOf(mapOf("key" to lastGeneratedId)))
                    "close" -> { statementCloseCount++; null }
                    "isClosed" -> false
                    "toString" -> "Fake Migration PreparedStatement"
                    else -> primitiveDefault(method.returnType)
                }
            }
        }

        private fun executeUpdate(sql: String, values: List<Any?>): Int {
            dmlCount++
            val lower = sql.lowercase()
            dmlCalls += DmlCall(sql, values)
            if (lower.contains("insert into") && lower.contains("system_migration_version")) {
                lastGeneratedId = nextGeneratedId++
                generatedIdForVersionInsert = lastGeneratedId
            }
            when {
                lower.contains("insert into") && lower.contains("system_migration_tag") -> {
                    events += "insert-tag:$sql"
                    lastGeneratedId = nextGeneratedId++
                }
                lower.contains("insert into") && lower.contains("system_migration") -> {
                    events += "insert-history:$sql"
                    insertHistoryCount++
                    if (!lower.contains("system_migration_version")) lastGeneratedId = nextGeneratedId++
                }
                lower.contains("delete") && lower.contains("system_migration_tag") -> {
                    events += "delete-tag:$sql"
                    deleteTagCount++
                    deleteCount++
                }
                lower.contains("delete") && lower.contains("system_migration") -> {
                    events += "delete-history:$sql"
                    deleteHistoryCount++
                    deleteCount++
                }
                else -> events += "dml:$sql"
            }
            if (dmlCount in failDmlCalls) throw SQLException("DML $dmlCount failed")
            return if (updateCounts.isEmpty()) 1 else updateCounts.removeFirst()
        }

        private fun resultSet(rows: List<Map<String, Any?>>, statementSql: String = ""): ResultSet {
            var index = -1
            var lastValue: Any? = null
            return proxy(ResultSet::class.java) { method, args ->
                when (method.name) {
                    "getMetaData" -> gog.my_project.data_base_migration.executor.support
                        .systemMigrationResultSetMetadata(statementSql)
                    "next" -> ++index < rows.size
                    "getInt", "getLong", "getString", "getObject" -> {
                        val row = rows.getOrNull(index) ?: error("ResultSet cursor is not on a row")
                        val value = if (args[0] is Number) {
                            row.values.elementAtOrNull((args[0] as Number).toInt() - 1)
                        } else {
                            row.entries.firstOrNull { it.key.equals(args[0].toString(), ignoreCase = true) }?.value
                        }
                        lastValue = value
                        when (method.name) {
                            "getInt" -> (value as? Number)?.toInt() ?: 0
                            "getLong" -> (value as? Number)?.toLong() ?: 0L
                            "getString" -> value?.toString()
                            else -> value
                        }
                    }
                    "wasNull" -> lastValue == null
                    "close" -> { resultSetCloseCount++; null }
                    "isClosed" -> false
                    "toString" -> "Fake Migration ResultSet"
                    else -> primitiveDefault(method.returnType)
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> proxy(type: Class<T>, invocation: (java.lang.reflect.Method, Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                invocation(method, args ?: emptyArray())
            } as T

        private fun primitiveDefault(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }

        data class DmlCall(val sql: String, val parameters: List<Any?>)
    }

    private companion object {
        const val VERSION_ID = 7
        const val VERSION_TAG_TYPE = 0
        const val MIGRATION_TAG_ID = 21
        const val MIGRATION_TAG_TYPE = 1
        const val APPLIED_ID = 31
        const val OTHER_APPLIED_ID = 32
        const val OTHER_VERSION_ID = 8
        const val GENERATED_TAG_ID = 1000L
        const val VERSION_TAG_ID_ONE = 51
        const val VERSION_TAG_ID_TWO = 52
        const val VERSION_TAG_ID_THREE = 53
        const val VERSION_TAG_ID_FOUR = 54
        const val VERSION_ID_ONE = 101
        const val VERSION_ID_TWO = 202
        const val VERSION_ID_THREE = 303
        const val VERSION_ID_FOUR = 404
        const val ITEM_TAG_ID_FIRST = 61
        const val ITEM_TAG_ID_SECOND = 62
        const val ITEM_TAG_ID_THIRD = 63
        const val ITEM_TAG_ID_FOURTH = 64
        const val ITEM_TAG_ID_FIFTH = 65
    }
}

private object MigrationItem : MigrationItemTag {
    override val value: String = "run_one_migration"
}

private class SampleMigration : Migration(MigrationItem) {
    override fun up(): MigrationDefinition {
        upCalls++
        upFailure?.let { throw it }
        return definition()
    }

    override fun down(): MigrationDefinition {
        downCalls++
        downFailure?.let { throw it }
        return definition()
    }

    private fun definition() = MigrationDefinition(
        Schema.createTable {
            table { tableName("migration_run_test") }
            addColumn {
                name("id")
                dataType(SchemaDataType.IntType)
                notNull()
            }
        },
    )

    companion object {
        var upCalls = 0
        var downCalls = 0
        var upFailure: Throwable? = null
        var downFailure: Throwable? = null

        fun reset() {
            upCalls = 0
            downCalls = 0
            upFailure = null
            downFailure = null
        }
    }
}

private object VersionTag : MigrationVersionTag {
    override val value: String = "version_transaction_test"
}

private object VersionTagOne : MigrationVersionTag {
    override val value: String = "orchestration_v1"
}

private object VersionTagTwo : MigrationVersionTag {
    override val value: String = "orchestration_v2"
}

private object VersionTagThree : MigrationVersionTag {
    override val value: String = "orchestration_v3"
}

private object VersionTagFour : MigrationVersionTag {
    override val value: String = "orchestration_v4_empty"
}

private object VersionItemFirst : MigrationItemTag {
    override val value: String = "version_transaction_first"
}

private object VersionItemSecond : MigrationItemTag {
    override val value: String = "version_transaction_second"
}

private object VersionItemThird : MigrationItemTag {
    override val value: String = "version_transaction_third"
}

private object VersionItemFourth : MigrationItemTag {
    override val value: String = "version_transaction_fourth"
}

private object VersionItemFifth : MigrationItemTag {
    override val value: String = "version_transaction_fifth"
}

private object UnsupportedDownItem : MigrationItemTag {
    override val value: String = "unsupported_down_item"
}

private object VersionMigrationTrace {
    val events = mutableListOf<String>()

    fun reset() = events.clear()
}

private class VersionFirstMigration : Migration(VersionItemFirst) {
    override fun up(): MigrationDefinition {
        VersionMigrationTrace.events += "up:first"
        return versionDefinition("version_first")
    }

    override fun down(): MigrationDefinition {
        VersionMigrationTrace.events += "down:first"
        return versionDefinition("version_first")
    }
}

private class VersionSecondMigration : Migration(VersionItemSecond) {
    override fun up(): MigrationDefinition {
        VersionMigrationTrace.events += "up:second"
        return versionDefinition("version_second")
    }

    override fun down(): MigrationDefinition {
        VersionMigrationTrace.events += "down:second"
        return versionDefinition("version_second")
    }
}

private class VersionThirdMigration : Migration(VersionItemThird) {
    override fun up(): MigrationDefinition {
        VersionMigrationTrace.events += "up:third"
        return versionDefinition("version_third")
    }

    override fun down(): MigrationDefinition {
        VersionMigrationTrace.events += "down:third"
        return versionDefinition("version_third")
    }
}

private class VersionFourthMigration : Migration(VersionItemFourth) {
    override fun up(): MigrationDefinition {
        VersionMigrationTrace.events += "up:fourth"
        return versionDefinition("version_fourth")
    }

    override fun down(): MigrationDefinition {
        VersionMigrationTrace.events += "down:fourth"
        return versionDefinition("version_fourth")
    }
}

private class VersionFifthMigration : Migration(VersionItemFifth) {
    override fun up(): MigrationDefinition {
        VersionMigrationTrace.events += "up:fifth"
        return versionDefinition("version_fifth")
    }

    override fun down(): MigrationDefinition {
        VersionMigrationTrace.events += "down:fifth"
        return versionDefinition("version_fifth")
    }
}

private class UnsupportedDownMigration : Migration(UnsupportedDownItem) {
    override fun up(): MigrationDefinition = versionDefinition("unsupported_down")

    override fun down(): MigrationDefinition {
        VersionMigrationTrace.events += "down:unsupported"
        throw UnsupportedOperationException("DOWN is not supported")
    }
}

private fun versionDefinition(tableName: String) = MigrationDefinition(
    Schema.createTable {
        table { tableName(tableName) }
        addColumn {
            name("id")
            dataType(SchemaDataType.IntType)
            notNull()
        }
    },
)
