package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base.migration.builder.createTable
import gog.my_project.data_base.migration.params.data_types.IntType
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MigrationExecutorCreateTableDefinitionsTest {
    @Test
    fun sendsDefinitionsThroughExecuteTableAndReturnsSuccess() {
        val manager = RecordingQueryExecute(ExecuteResult.Success(true))
        val executor = MigrationExecutor(queryExecutor = manager)
        val previous = DefaultDatabaseConfig.config
        var received: ExecuteResult<Boolean>? = null

        try {
            DefaultDatabaseConfig.config = previous.copy(dialect = DialectQuery.MY_SQL)
            executor.execute(
                queryBuilder = createTable {
                    table { tableName("orders") }
                    addColumn { name("tenant_id"); dataType(IntType()); notNull() }
                    addColumn { name("order_id"); dataType(IntType()); notNull() }
                    primaryKey { columns("tenant_id", "order_id") }
                    index { name("idx_orders_tenant"); columns("tenant_id"); using(IndexMethod.BTREE) }
                    foreignKey {
                        name("fk_orders_user")
                        columns("tenant_id", "order_id")
                        referencesTable("users")
                        referencesColumns("tenant_id", "id")
                        onDelete(ForeignKeyAction.CASCADE)
                    }
                },
                blockExecute = { received = it },
            )
        } finally {
            DefaultDatabaseConfig.config = previous
        }

        assertEquals(
            """CREATE TABLE `orders` (
  `tenant_id` INT NOT NULL,
  `order_id` INT NOT NULL,
  PRIMARY KEY (`tenant_id`, `order_id`),
  INDEX `idx_orders_tenant` (`tenant_id`) USING BTREE,
  CONSTRAINT `fk_orders_user` FOREIGN KEY (`tenant_id`, `order_id`) REFERENCES `users` (`tenant_id`, `id`) ON DELETE CASCADE
)""",
            manager.executedQuery?.query,
        )
        assertEquals(ExecuteResult.Success(true), received)
    }

    @Test
    fun propagatesManagerFailureWithoutSelectPreflight() {
        val failure = IllegalStateException("table constraint rejected")
        val manager = RecordingQueryExecute(ExecuteResult.Failure(failure))
        val executor = MigrationExecutor(queryExecutor = manager)
        val previous = DefaultDatabaseConfig.config
        var received: ExecuteResult<Boolean>? = null

        try {
            DefaultDatabaseConfig.config = previous.copy(dialect = DialectQuery.MY_SQL)
            executor.execute(
                queryBuilder = createTable {
                    table { tableName("lookup") }
                    addColumn { name("id"); dataType(IntType()); notNull() }
                    unique { name("uq_lookup_id"); columns("id") }
                },
                blockExecute = { received = it },
            )
        } finally {
            DefaultDatabaseConfig.config = previous
        }

        assertEquals(ExecuteResult.Failure(failure), received)
        assertSame(failure, (received as ExecuteResult.Failure).exception)
    }

    private class RecordingQueryExecute(
        private val result: ExecuteResult<Boolean>,
    ) : IQueryExecute {
        var executedQuery: BuiltQuery? = null

        override fun executeTable(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Boolean>) -> Unit) {
            executedQuery = builtQuery
            blockExecute(result)
        }

        override fun executeSelect(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<ResultSet>) -> Unit) =
            error("Unexpected preflight select while executing CREATE TABLE")
        override fun executeUpdate(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected update while executing CREATE TABLE")
        override fun executeInsert(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Long>) -> Unit) =
            error("Unexpected insert while executing CREATE TABLE")
        override fun executeDelete(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected delete while executing CREATE TABLE")
    }
}
