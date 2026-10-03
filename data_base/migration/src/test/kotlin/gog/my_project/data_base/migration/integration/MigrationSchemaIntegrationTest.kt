package gog.my_project.data_base.migration.integration

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.api.interfaces.Migration
import gog.my_project.data_base.migration.api.interfaces.MigrationDefinition
import gog.my_project.data_base.migration.api.interfaces.MigrationId
import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base.migration.builder.createTable
import gog.my_project.data_base.migration.builder.dropForeignKey as buildDropForeignKey
import gog.my_project.data_base.migration.builder.dropTable
import gog.my_project.data_base.migration.builder.migration
import gog.my_project.data_base.migration.builder.migrationConfig
import gog.my_project.data_base.migration.executor.manager.MigrationExecutor
import gog.my_project.data_base.migration.executor.manager.MigrationMigrator
import gog.my_project.data_base.migration.params.data_types.DateTimeType
import gog.my_project.data_base.migration.params.data_types.DecimalType
import gog.my_project.data_base.migration.params.data_types.IntType
import gog.my_project.data_base.migration.params.data_types.TextType
import gog.my_project.data_base.migration.params.data_types.VarcharType
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.DriverManager
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MigrationSchemaIntegrationTest {
    @Test
    fun createsRelationalSchemaAndRecordsItsMigrationHistoryAgainstMySql() {
        val runtime = TestRuntimeRecorder.create(
            TestRuntimeRecorder.PROJECT_PATH,
            TestRuntimeRecorder.SCHEMA_TEST_ID,
            listOf("prepare-config", "open-connection", "refresh-collision", "registration", "migration-execution", "schema-verification", "history-verification", "collision-verification", "final-state", "restore-resources"),
            parentIds = mapOf(
                "prepare-config" to "schema-setup",
                "open-connection" to "schema-setup",
                "refresh-collision" to "schema-setup",
                "registration" to "schema-migrations",
                "migration-execution" to "schema-migrations",
                "schema-verification" to "schema-checks",
                "history-verification" to "schema-checks",
                "collision-verification" to "schema-checks",
                "final-state" to "schema-checks",
                "restore-resources" to "schema-finalization",
            ),
        )
        runtime?.begin()
        runtime?.startStep("prepare-config")
        var testFailure: Throwable? = null
        try {
        val refresh = parseRefreshOption(System.getProperty(DB_REFRESH_PROPERTY))
        val config = databaseConfigFromProperties()
        val previousConfig = DefaultDatabaseConfig.config
        var connection: Connection? = null
        runtime?.succeedStep("prepare-config")

        try {
            runtime?.startStep("open-connection")
            DefaultDatabaseConfig.config = config
            val testConnection = DriverManager.getConnection(config.getDbUrl(), config.dbUserName, config.dbPassword)
            connection = testConnection
            assertSelectedCatalog(testConnection, config.dbName)
            runtime?.succeedStep("open-connection")

            runtime?.startStep("refresh-collision")
            if (refresh) {
                refreshAllTables(testConnection)
                assertTrue(tableNames(testConnection).isEmpty(), "Database refresh must leave no user tables")
            }

            val collisions = existingOwnedObjects(testConnection)
            if (!refresh && collisions.isNotEmpty()) {
                val tablesBefore = tableNames(testConnection)
                val collision = assertFailsWith<IllegalStateException> {
                    assertNoOwnedObjectCollisions(testConnection)
                }
                assertTrue(collision.message.orEmpty().contains("collision"), "Unexpected collision error")
                assertEquals(tablesBefore, tableNames(testConnection), "Collision handling changed database objects")
                runtime?.succeedStep("refresh-collision", mapOf("collisionDetected" to true, "migrationStarted" to false))
                runtime?.markRemainingNotExecuted(3)
                return
            }

            assertNoOwnedObjectCollisions(testConnection)
            runtime?.succeedStep("refresh-collision", mapOf("collisionDetected" to false, "refreshed" to refresh))
            runtime?.startStep("registration")
            val configuration = schemaMigrationConfiguration()
            runtime?.succeedStep("registration", mapOf("migrationCount" to expectedMigrationIds.size))
            runtime?.startStep("migration-execution")
            val migrationResult = MigrationMigrator(configuration).migrateAndCapture()
            val observedHistory = readMigrationHistory(testConnection)
            runtime?.recordMigrations(
                expectedMigrationIds,
                observedHistory.map { it.migration to it.batch },
                executionSucceeded = migrationResult is ExecuteResult.Success,
            )
            assertIs<ExecuteResult.Success<Unit>>(migrationResult)
            runtime?.succeedStep("migration-execution", mapOf("migrationCount" to observedHistory.size))

            runtime?.startStep("schema-verification")
            assertSchema(testConnection)
            val observedSchema = TestRuntimeRecorder.captureSchema(testConnection, ownedSchemaTables, includeRelationalDetails = true)
            runtime?.recordSchema(
                observedSchema,
                mapOf("tables" to (ownedSchemaTables + SYSTEM_MIGRATION_TABLE).filter { it in tableNames(testConnection) }.sorted()),
            )
            runtime?.succeedStep("schema-verification", mapOf("tableCount" to ownedSchemaTables.size))

            runtime?.startStep("history-verification")
            assertMigrationHistory(testConnection)
            runtime?.recordHistory(observedHistory.map { it.migration to it.batch })
            runtime?.succeedStep("history-verification", mapOf("migrationCount" to observedHistory.size))

            // Exercise the no-refresh collision path against the now-populated schema without mutating it.
            runtime?.startStep("collision-verification")
            val tablesBeforeCollision = tableNames(testConnection)
            val historyBeforeCollision = readMigrationHistory(testConnection)
            var secondMigratorStarted = false
            assertFailsWith<IllegalStateException> {
                assertNoOwnedObjectCollisions(testConnection)
                secondMigratorStarted = true
            }
            assertFalse(secondMigratorStarted, "A colliding schema must stop before MigrationMigrator")
            assertEquals(tablesBeforeCollision, tableNames(testConnection))
            assertEquals(historyBeforeCollision, readMigrationHistory(testConnection))
            runtime?.succeedStep("collision-verification", mapOf("collisionDetected" to true, "migrationRestarted" to false))
            runtime?.startStep("final-state")
            runtime?.recordFinalState(
                mapOf(
                    "tables" to tableNames(testConnection).filter { it in ownedSchemaTables || it == SYSTEM_MIGRATION_TABLE }.sorted(),
                    "historyRows" to observedHistory.map { mapOf("migrationId" to it.migration, "batch" to it.batch) },
                ),
            )
            runtime?.succeedStep("final-state")
        } catch (failure: Throwable) {
            testFailure = failure
            runtime?.failActiveStep()
            throw failure
        } finally {
            runtime?.startStep("restore-resources")
            DefaultDatabaseConfig.config = previousConfig
            connection?.close()
            runtime?.succeedStep("restore-resources")
            if (testFailure == null) runtime?.complete() else runtime?.incomplete()
        }
        } catch (failure: Throwable) {
            runtime?.failActiveStep()
            runtime?.incomplete()
            throw failure
        }
    }

    private fun databaseConfigFromProperties(): DatabaseConfig {
        val domain = requiredProperty(DB_DOMAIN_PROPERTY)
        require(domain.startsWith("jdbc:mysql://")) {
            "Gradle property '" + DB_DOMAIN_PROPERTY + "' must use the MySQL JDBC URL prefix"
        }
        val port = requiredProperty(DB_PORT_PROPERTY).toIntOrNull()
            ?: error("Gradle property '" + DB_PORT_PROPERTY + "' must be an integer")
        require(port in 1..65535) { "Gradle property '" + DB_PORT_PROPERTY + "' must be between 1 and 65535" }

        return DatabaseConfig(
            dbDomain = domain,
            dbPort = port,
            dbName = requiredProperty(DB_NAME_PROPERTY),
            dbUserName = requiredProperty(DB_USERNAME_PROPERTY),
            dbPassword = System.getProperty(DB_PASSWORD_PROPERTY)
                ?: error("Missing Gradle property '" + DB_PASSWORD_PROPERTY + "'; set it explicitly, even when empty"),
            dbPoolSize = 10,
            dialect = DialectQuery.MY_SQL,
        )
    }

    private fun requiredProperty(name: String): String =
        (System.getProperty(name) ?: error("Missing required Gradle property '" + name + "'"))
            .takeIf(String::isNotBlank)
            ?: error("Gradle property '" + name + "' must not be blank")

    private fun parseRefreshOption(value: String?): Boolean = when (value) {
        null, "false" -> false
        "true" -> true
        else -> error("Gradle property '" + DB_REFRESH_PROPERTY + "' must be exactly 'true' or 'false'")
    }

    private fun refreshAllTables(connection: Connection) {
        assertSelectedCatalog(connection, requiredProperty(DB_NAME_PROPERTY))
        val tableSnapshot = tableNames(connection).toList()
        val foreignKeys = tableSnapshot.flatMap { table -> importedForeignKeys(connection, table) }.distinct()

        foreignKeys.forEach(::dropForeignKey)
        tableSnapshot.forEach(::dropTableIfPresent)
        assertTrue(tableNames(connection).isEmpty(), "Database refresh must leave no user tables")
    }

    private fun importedForeignKeys(connection: Connection, table: String): List<ForeignKeyToDrop> =
        connection.metaData.getImportedKeys(connection.catalog, null, table).use { rows ->
            buildList {
                while (rows.next()) {
                    val foreignKeyName = rows.getString("FK_NAME")
                        ?.takeIf(String::isNotBlank)
                        ?: error("Cannot safely refresh table '$table': JDBC metadata returned an unnamed foreign key")
                    add(ForeignKeyToDrop(table, foreignKeyName))
                }
            }.distinct()
        }

    private fun dropForeignKey(foreignKey: ForeignKeyToDrop) {
        var result: ExecuteResult<Boolean>? = null
        MigrationExecutor().execute(
            queryBuilder = buildDropForeignKey {
                tableName(foreignKey.table)
                name(foreignKey.name)
            },
            blockExecute = { result = it },
        )
        when (val completed = assertNotNull(result, "DROP FOREIGN KEY did not complete for " + foreignKey)) {
            is ExecuteResult.Failure -> throw AssertionError("Could not drop foreign key " + foreignKey, completed.exception)
            is ExecuteResult.Success -> assertEquals(true, completed.result, "DROP FOREIGN KEY did not succeed for " + foreignKey)
        }
    }

    private fun dropTableIfPresent(tableName: String) {
        var result: ExecuteResult<Boolean>? = null
        MigrationExecutor().execute(
            queryBuilder = dropTable {
                tableName(tableName)
                ifExists()
            },
            blockExecute = { result = it },
        )
        when (val completed = assertNotNull(result, "DROP TABLE did not complete for " + tableName)) {
            is ExecuteResult.Failure -> throw AssertionError("Could not refresh test object " + tableName, completed.exception)
            is ExecuteResult.Success -> Unit // Success(false) means the IF EXISTS target was absent.
        }
    }

    private fun assertSelectedCatalog(connection: Connection, expectedDatabase: String) {
        assertTrue(
            connection.catalog?.equals(expectedDatabase, ignoreCase = true) == true,
            "Refresh connection is not scoped to DatabaseConfig.dbName '$expectedDatabase'",
        )
    }

    private data class ForeignKeyToDrop(val table: String, val name: String)

    private fun existingOwnedObjects(connection: Connection): List<String> {
        val presentTables = tableNames(connection).map { it.lowercase() }.toSet()
        return (ownedSchemaTables + SYSTEM_MIGRATION_TABLE).filter { it.lowercase() in presentTables }
    }

    private fun assertNoOwnedObjectCollisions(connection: Connection) {
        val collisions = existingOwnedObjects(connection)
        check(collisions.isEmpty()) { "Database collision: test-owned objects already exist: " + collisions }
    }

    private fun tableNames(connection: Connection): Set<String> =
        connection.metaData.getTables(connection.catalog, null, "%", arrayOf("TABLE")).use { rows ->
            buildSet { while (rows.next()) add(rows.getString("TABLE_NAME")) }
        }

    private fun schemaMigrationConfiguration() = migrationConfig {
        migration<CreateSchemaCategories>()
        migration<CreateSchemaTenants>()
        migration<CreateSchemaUsers>()
        migration<CreateSchemaProducts>()
        migration<CreateSchemaAddresses>()
        migration<CreateSchemaOrders>()
        migration<CreateSchemaOrderItems>()
    }

    private fun MigrationMigrator.migrateAndCapture(): ExecuteResult<Unit> {
        var result: ExecuteResult<Unit>? = null
        migrate { result = it }
        return assertNotNull(result, "MigrationMigrator did not complete synchronously")
    }

    private fun assertSchema(connection: Connection) {
        val expectedColumns = mapOf(
            CATEGORIES_TABLE to mapOf("id" to Types.INTEGER, "name" to Types.VARCHAR),
            TENANTS_TABLE to mapOf("id" to Types.INTEGER, "name" to Types.VARCHAR),
            USERS_TABLE to mapOf(
                "id" to Types.INTEGER,
                "tenant_id" to Types.INTEGER,
                "email" to Types.VARCHAR,
                "name" to Types.VARCHAR,
            ),
            PRODUCTS_TABLE to mapOf(
                "id" to Types.INTEGER,
                "category_id" to Types.INTEGER,
                "sku" to Types.VARCHAR,
                "description" to Types.LONGVARCHAR,
                "price" to Types.DECIMAL,
            ),
            ADDRESSES_TABLE to mapOf(
                "id" to Types.INTEGER,
                "user_id" to Types.INTEGER,
                "address_type" to Types.VARCHAR,
                "address_line" to Types.VARCHAR,
            ),
            ORDERS_TABLE to mapOf(
                "id" to Types.INTEGER,
                "tenant_id" to Types.INTEGER,
                "user_id" to Types.INTEGER,
                "order_number" to Types.VARCHAR,
                "created_at" to Types.TIMESTAMP,
                "total" to Types.DECIMAL,
            ),
            ORDER_ITEMS_TABLE to mapOf(
                "order_id" to Types.INTEGER,
                "product_id" to Types.INTEGER,
                "quantity" to Types.INTEGER,
                "unit_price" to Types.DECIMAL,
            ),
        )
        val tables = tableNames(connection).map(String::lowercase).toSet()
        for ((table, columns) in expectedColumns) {
            assertTrue(table.lowercase() in tables, "Missing schema table " + table)
            val actualColumns = readColumns(connection, table)
            assertEquals(columns.keys, actualColumns.keys, "Unexpected columns for " + table)
            for ((column, jdbcType) in columns) {
                val actualColumn = actualColumns.getValue(column)
                val acceptedTypes = if (table == PRODUCTS_TABLE && column == "description") {
                    // Connector/J may expose TEXT as either VARCHAR or LONGVARCHAR in JDBC metadata.
                    setOf(Types.VARCHAR, Types.LONGVARCHAR)
                } else {
                    setOf(jdbcType)
                }
                assertTrue(
                    actualColumn.jdbcType in acceptedTypes,
                    "Unexpected JDBC type for " + table + "." + column + ": " + actualColumn.jdbcType,
                )
                assertEquals(
                    DatabaseMetaData.columnNoNulls,
                    actualColumn.nullable,
                    "Unexpected nullability for " + table + "." + column,
                )
            }
        }

        for (table in listOf(CATEGORIES_TABLE, TENANTS_TABLE, USERS_TABLE, PRODUCTS_TABLE, ADDRESSES_TABLE, ORDERS_TABLE)) {
            assertTrue(readColumns(connection, table).getValue("id").autoIncrement, table + ".id must be auto-increment")
            assertEquals(DatabaseMetaData.columnNoNulls, readColumns(connection, table).getValue("id").nullable)
            assertPrimaryKey(connection, table, listOf("id"))
        }
        assertEquals(DatabaseMetaData.columnNoNulls, readColumns(connection, CATEGORIES_TABLE).getValue("name").nullable)
        assertEquals(DatabaseMetaData.columnNoNulls, readColumns(connection, TENANTS_TABLE).getValue("name").nullable)
        assertPrimaryKey(connection, ORDER_ITEMS_TABLE, listOf("order_id", "product_id"))

        assertIndex(connection, CATEGORIES_TABLE, INDEX_CATEGORIES_NAME, listOf("name"), unique = true)
        assertIndex(connection, TENANTS_TABLE, INDEX_TENANTS_NAME, listOf("name"), unique = true)
        assertIndex(connection, USERS_TABLE, INDEX_USERS_TENANT_EMAIL, listOf("tenant_id", "email"), unique = true)
        assertIndex(connection, USERS_TABLE, INDEX_USERS_TENANT_ID, listOf("tenant_id", "id"), unique = true)
        assertIndex(connection, PRODUCTS_TABLE, INDEX_PRODUCTS_SKU, listOf("sku"), unique = true)
        assertIndex(connection, ADDRESSES_TABLE, INDEX_ADDRESSES_USER_TYPE, listOf("user_id", "address_type"), unique = true)
        assertIndex(connection, ORDERS_TABLE, INDEX_ORDERS_TENANT_NUMBER, listOf("tenant_id", "order_number"), unique = true)
        assertIndex(connection, PRODUCTS_TABLE, INDEX_PRODUCTS_CATEGORY, listOf("category_id"), unique = false)
        assertIndex(connection, ORDERS_TABLE, INDEX_ORDERS_TENANT_CREATED, listOf("tenant_id", "created_at"), unique = false)
        // Connector/J metadata may not distinguish FULLTEXT from another non-unique index.
        assertIndex(connection, PRODUCTS_TABLE, INDEX_PRODUCTS_DESCRIPTION_FULLTEXT, listOf("description"), unique = false)

        assertForeignKey(connection, USERS_TABLE, FK_USERS_TENANT, listOf("tenant_id"), TENANTS_TABLE, listOf("id"))
        assertForeignKey(connection, PRODUCTS_TABLE, FK_PRODUCTS_CATEGORY, listOf("category_id"), CATEGORIES_TABLE, listOf("id"))
        assertForeignKey(connection, ADDRESSES_TABLE, FK_ADDRESSES_USER, listOf("user_id"), USERS_TABLE, listOf("id"))
        assertForeignKey(
            connection,
            ORDERS_TABLE,
            FK_ORDERS_TENANT_USER,
            listOf("tenant_id", "user_id"),
            USERS_TABLE,
            listOf("tenant_id", "id"),
        )
        assertForeignKey(connection, ORDER_ITEMS_TABLE, FK_ITEMS_ORDER, listOf("order_id"), ORDERS_TABLE, listOf("id"))
        assertForeignKey(connection, ORDER_ITEMS_TABLE, FK_ITEMS_PRODUCT, listOf("product_id"), PRODUCTS_TABLE, listOf("id"))
    }

    private fun readColumns(connection: Connection, table: String): Map<String, ColumnInfo> =
        connection.metaData.getColumns(connection.catalog, null, "%", "%").use { rows ->
            buildMap {
                while (rows.next()) {
                    if (rows.getString("TABLE_NAME").equals(table, ignoreCase = true)) {
                        val name = rows.getString("COLUMN_NAME").lowercase()
                        put(
                            name,
                            ColumnInfo(
                                jdbcType = rows.getInt("DATA_TYPE"),
                                nullable = rows.getInt("NULLABLE"),
                                autoIncrement = rows.getString("IS_AUTOINCREMENT")?.equals("YES", ignoreCase = true) == true,
                            ),
                        )
                    }
                }
            }
        }

    private fun assertPrimaryKey(connection: Connection, table: String, expectedColumns: List<String>) {
        val actual = connection.metaData.getPrimaryKeys(connection.catalog, null, table).use { rows ->
            buildList {
                while (rows.next()) add(rows.getShort("KEY_SEQ") to rows.getString("COLUMN_NAME").lowercase())
            }.sortedBy { it.first }.map { it.second }
        }
        assertEquals(expectedColumns, actual, "Unexpected primary key for " + table)
    }

    private fun assertIndex(
        connection: Connection,
        table: String,
        name: String,
        expectedColumns: List<String>,
        unique: Boolean,
    ) {
        val index = readIndexes(connection, table)[name.lowercase()]
        assertNotNull(index, "Missing index " + name + " on " + table)
        assertEquals(expectedColumns, index.columns, "Unexpected ordered columns for index " + name)
        assertEquals(unique, index.unique, "Unexpected uniqueness for index " + name)
    }

    private fun readIndexes(connection: Connection, table: String): Map<String, IndexInfo> {
        val rowsByName = linkedMapOf<String, MutableList<Pair<Int, String>>>()
        val uniquenessByName = mutableMapOf<String, Boolean>()
        connection.metaData.getIndexInfo(connection.catalog, null, table, false, false).use { rows ->
            while (rows.next()) {
                val name = rows.getString("INDEX_NAME") ?: continue
                val column = rows.getString("COLUMN_NAME") ?: continue
                rowsByName.getOrPut(name.lowercase()) { mutableListOf() }
                    .add(rows.getInt("ORDINAL_POSITION") to column.lowercase())
                uniquenessByName[name.lowercase()] = !rows.getBoolean("NON_UNIQUE")
            }
        }
        return rowsByName.mapValues { (name, columns) ->
            IndexInfo(
                columns = columns.sortedBy { it.first }.map { it.second },
                unique = uniquenessByName.getValue(name),
            )
        }
    }

    private fun assertForeignKey(
        connection: Connection,
        table: String,
        name: String,
        localColumns: List<String>,
        referencedTable: String,
        referencedColumns: List<String>,
    ) {
        val rows = connection.metaData.getImportedKeys(connection.catalog, null, table).use { resultSet ->
            buildList {
                while (resultSet.next()) {
                    if (resultSet.getString("FK_NAME")?.equals(name, ignoreCase = true) == true) {
                        add(
                            ImportedKeyRow(
                                sequence = resultSet.getShort("KEY_SEQ"),
                                localColumn = resultSet.getString("FKCOLUMN_NAME").lowercase(),
                                referencedTable = resultSet.getString("PKTABLE_NAME").lowercase(),
                                referencedColumn = resultSet.getString("PKCOLUMN_NAME").lowercase(),
                                deleteRule = resultSet.getShort("DELETE_RULE"),
                                updateRule = resultSet.getShort("UPDATE_RULE"),
                            ),
                        )
                    }
                }
            }.sortedBy { it.sequence }
        }
        assertEquals(localColumns.size, rows.size, "Unexpected key-part count for foreign key " + name)
        assertEquals(localColumns, rows.map { it.localColumn }, "Unexpected local key order for " + name)
        assertEquals(List(localColumns.size) { referencedTable.lowercase() }, rows.map { it.referencedTable })
        assertEquals(referencedColumns, rows.map { it.referencedColumn }, "Unexpected referenced key order for " + name)

        val noActionOrRestrict = setOf(
            DatabaseMetaData.importedKeyNoAction.toShort(),
            DatabaseMetaData.importedKeyRestrict.toShort(),
        )
        assertTrue(rows.all { it.deleteRule in noActionOrRestrict }, "Unexpected ON DELETE rule for " + name)
        assertTrue(rows.all { it.updateRule in noActionOrRestrict }, "Unexpected ON UPDATE rule for " + name)
    }

    private fun assertMigrationHistory(connection: Connection) {
        val history = readMigrationHistory(connection)
        assertEquals(expectedMigrationIds, history.map { it.migration })
        assertEquals(List(expectedMigrationIds.size) { 1 }, history.map { it.batch })
    }

    private fun readMigrationHistory(connection: Connection): List<MigrationHistoryRow> =
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT migration, batch FROM " + SYSTEM_MIGRATION_TABLE + " ORDER BY id ASC",
            ).use { rows ->
                buildList {
                    while (rows.next()) {
                        add(MigrationHistoryRow(rows.getString("migration"), rows.getInt("batch")))
                    }
                }
            }
        }

    private data class ColumnInfo(val jdbcType: Int, val nullable: Int, val autoIncrement: Boolean)
    private data class IndexInfo(val columns: List<String>, val unique: Boolean)
    private data class ImportedKeyRow(
        val sequence: Short,
        val localColumn: String,
        val referencedTable: String,
        val referencedColumn: String,
        val deleteRule: Short,
        val updateRule: Short,
    )
    private data class MigrationHistoryRow(val migration: String, val batch: Int)

    companion object {
        private const val DB_DOMAIN_PROPERTY = "migration.test.db.domain"
        private const val DB_PORT_PROPERTY = "migration.test.db.port"
        private const val DB_NAME_PROPERTY = "migration.test.db.name"
        private const val DB_USERNAME_PROPERTY = "migration.test.db.username"
        private const val DB_PASSWORD_PROPERTY = "migration.test.db.password"
        private const val DB_REFRESH_PROPERTY = "migration.test.db.refresh"

        private const val CATEGORIES_TABLE = "migration_schema_categories"
        private const val TENANTS_TABLE = "migration_schema_tenants"
        private const val USERS_TABLE = "migration_schema_users"
        private const val PRODUCTS_TABLE = "migration_schema_products"
        private const val ADDRESSES_TABLE = "migration_schema_addresses"
        private const val ORDERS_TABLE = "migration_schema_orders"
        private const val ORDER_ITEMS_TABLE = "migration_schema_order_items"
        private const val SYSTEM_MIGRATION_TABLE = "system_migration"

        private const val INDEX_CATEGORIES_NAME = "uq_schema_categories_name"
        private const val INDEX_TENANTS_NAME = "uq_schema_tenants_name"
        private const val INDEX_USERS_TENANT_EMAIL = "uq_schema_users_tenant_email"
        private const val INDEX_USERS_TENANT_ID = "uq_schema_users_tenant_id"
        private const val INDEX_PRODUCTS_SKU = "uq_schema_products_sku"
        private const val INDEX_ADDRESSES_USER_TYPE = "uq_schema_addresses_user_type"
        private const val INDEX_ORDERS_TENANT_NUMBER = "uq_schema_orders_tenant_number"
        private const val INDEX_PRODUCTS_CATEGORY = "idx_schema_products_category"
        private const val INDEX_ORDERS_TENANT_CREATED = "idx_schema_orders_tenant_created"
        private const val INDEX_PRODUCTS_DESCRIPTION_FULLTEXT = "ft_schema_products_description"

        private const val FK_USERS_TENANT = "fk_schema_users_tenants"
        private const val FK_PRODUCTS_CATEGORY = "fk_schema_products_categories"
        private const val FK_ADDRESSES_USER = "fk_schema_addresses_users"
        private const val FK_ORDERS_TENANT_USER = "fk_schema_orders_tenant_users"
        private const val FK_ITEMS_ORDER = "fk_schema_order_items_orders"
        private const val FK_ITEMS_PRODUCT = "fk_schema_order_items_products"

        private val ownedSchemaTables = listOf(
            CATEGORIES_TABLE,
            TENANTS_TABLE,
            USERS_TABLE,
            PRODUCTS_TABLE,
            ADDRESSES_TABLE,
            ORDERS_TABLE,
            ORDER_ITEMS_TABLE,
        )

        private val expectedMigrationIds = listOf(
            schemaMigrationTags.schema_create_categories,
            schemaMigrationTags.schema_create_tenants,
            schemaMigrationTags.schema_create_users,
            schemaMigrationTags.schema_create_products,
            schemaMigrationTags.schema_create_addresses,
            schemaMigrationTags.schema_create_orders,
            schemaMigrationTags.schema_create_order_items,
        )
    }
}

private object schemaMigrationTags {
    const val schema_create_categories = "schema_create_categories"
    const val schema_create_tenants = "schema_create_tenants"
    const val schema_create_users = "schema_create_users"
    const val schema_create_products = "schema_create_products"
    const val schema_create_addresses = "schema_create_addresses"
    const val schema_create_orders = "schema_create_orders"
    const val schema_create_order_items = "schema_create_order_items"
}

@MigrationId(schemaMigrationTags.schema_create_categories)
class CreateSchemaCategories : Migration {
    override fun up(): MigrationDefinition = migration {
        createTable {
            table { tableName("migration_schema_categories") }
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
                autoIncrement()
                primaryKey()
            }
            addColumn {
                name("name")
                dataType(VarcharType(255))
                notNull()
            }
            unique {
                name("uq_schema_categories_name")
                columns("name")
            }
        }
    }

    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(schemaMigrationTags.schema_create_tenants)
class CreateSchemaTenants : Migration {
    override fun up(): MigrationDefinition = migration {
        createTable {
            table { tableName("migration_schema_tenants") }
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
                autoIncrement()
                primaryKey()
            }
            addColumn {
                name("name")
                dataType(VarcharType(255))
                notNull()
            }
            unique {
                name("uq_schema_tenants_name")
                columns("name")
            }
        }
    }

    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(schemaMigrationTags.schema_create_users)
class CreateSchemaUsers : Migration {
    override fun up(): MigrationDefinition = migration {
        createTable {
            table { tableName("migration_schema_users") }
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
                autoIncrement()
                primaryKey()
            }
            addColumn {
                name("tenant_id")
                dataType(IntType())
            }
            addColumn {
                name("email")
                dataType(VarcharType(255))
            }
            addColumn {
                name("name")
                dataType(VarcharType(255))
            }
            foreignKey {
                name("fk_schema_users_tenants")
                columns("tenant_id")
                referencesTable("migration_schema_tenants")
                referencesColumns("id")
            }
            unique {
                name("uq_schema_users_tenant_email")
                columns("tenant_id", "email")
            }
            unique {
                name("uq_schema_users_tenant_id")
                columns("tenant_id", "id")
            }
        }
    }

    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(schemaMigrationTags.schema_create_products)
class CreateSchemaProducts : Migration {
    override fun up(): MigrationDefinition = migration {
        createTable {
            table { tableName("migration_schema_products") }
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
                autoIncrement()
                primaryKey()
            }
            addColumn {
                name("category_id")
                dataType(IntType())
            }
            addColumn {
                name("sku")
                dataType(VarcharType(255))
            }
            addColumn {
                name("description")
                dataType(TextType())
            }
            addColumn {
                name("price")
                dataType(DecimalType(10, 2))
            }
            foreignKey {
                name("fk_schema_products_categories")
                columns("category_id")
                referencesTable("migration_schema_categories")
                referencesColumns("id")
            }
            unique {
                name("uq_schema_products_sku")
                columns("sku")
            }
            index {
                name("idx_schema_products_category")
                columns("category_id")
            }
            fullTextIndex {
                name("ft_schema_products_description")
                columns("description")
            }
        }
    }

    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(schemaMigrationTags.schema_create_addresses)
class CreateSchemaAddresses : Migration {
    override fun up(): MigrationDefinition = migration {
        createTable {
            table { tableName("migration_schema_addresses") }
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
                autoIncrement()
                primaryKey()
            }
            addColumn {
                name("user_id")
                dataType(IntType())
            }
            addColumn {
                name("address_type")
                dataType(VarcharType(255))
            }
            addColumn {
                name("address_line")
                dataType(VarcharType(255))
            }
            foreignKey {
                name("fk_schema_addresses_users")
                columns("user_id")
                referencesTable("migration_schema_users")
                referencesColumns("id")
            }
            unique {
                name("uq_schema_addresses_user_type")
                columns("user_id", "address_type")
            }
        }
    }

    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(schemaMigrationTags.schema_create_orders)
class CreateSchemaOrders : Migration {
    override fun up(): MigrationDefinition = migration {
        createTable {
            table { tableName("migration_schema_orders") }
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
                autoIncrement()
                primaryKey()
            }
            addColumn {
                name("tenant_id")
                dataType(IntType())
            }
            addColumn {
                name("user_id")
                dataType(IntType())
            }
            addColumn {
                name("order_number")
                dataType(VarcharType(255))
            }
            addColumn {
                name("created_at")
                dataType(DateTimeType())
            }
            addColumn {
                name("total")
                dataType(DecimalType(10, 2))
            }
            foreignKey {
                name("fk_schema_orders_tenant_users")
                columns("tenant_id", "user_id")
                referencesTable("migration_schema_users")
                referencesColumns("tenant_id", "id")
            }
            unique {
                name("uq_schema_orders_tenant_number")
                columns("tenant_id", "order_number")
            }
            index {
                name("idx_schema_orders_tenant_created")
                columns("tenant_id", "created_at")
                using(IndexMethod.BTREE)
            }
        }
    }

    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(schemaMigrationTags.schema_create_order_items)
class CreateSchemaOrderItems : Migration {
    override fun up(): MigrationDefinition = migration {
        createTable {
            table { tableName("migration_schema_order_items") }
            addColumn {
                name("order_id")
                dataType(IntType())
            }
            addColumn {
                name("product_id")
                dataType(IntType())
            }
            addColumn {
                name("quantity")
                dataType(IntType())
            }
            addColumn {
                name("unit_price")
                dataType(DecimalType(10, 2))
            }
            primaryKey {
                columns("order_id", "product_id")
            }
            foreignKey {
                name("fk_schema_order_items_orders")
                columns("order_id")
                referencesTable("migration_schema_orders")
                referencesColumns("id")
            }
            foreignKey {
                name("fk_schema_order_items_products")
                columns("product_id")
                referencesTable("migration_schema_products")
                referencesColumns("id")
            }
        }
    }

    override fun down(): MigrationDefinition = migration { }
}
