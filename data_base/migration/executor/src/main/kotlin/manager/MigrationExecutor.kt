package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.data_base.DatabaseProduct
import gog.my_project.data_base.core.data_base.MARIA_DB
import gog.my_project.data_base.core.data_base.MYSQL
import gog.my_project.data_base.core.data_base.ProductDatabaseVersion
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.api.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableApi
import gog.my_project.data_base_schema.api.interfaces.add_column.ISchemaAddColumnApi
import gog.my_project.data_base_schema.api.interfaces.drop_column.ISchemaDropColumnApi
import gog.my_project.data_base_schema.api.interfaces.drop_table.ISchemaDropTableApi
import gog.my_project.data_base_schema.api.interfaces.rename_table.ISchemaRenameTableApi
import gog.my_project.data_base_schema.api.interfaces.rename_column.ISchemaRenameColumnApi
import gog.my_project.data_base_schema.api.interfaces.modify_column.ISchemaModifyColumnApi
import gog.my_project.data_base_schema.api.interfaces.create_index.ISchemaCreateIndexApi
import gog.my_project.data_base_schema.api.interfaces.drop_index.ISchemaDropIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_unique_index.ISchemaCreateUniqueIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_multi_column_index.ISchemaCreateMultiColumnIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_full_text_index.ISchemaCreateFullTextIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_spatial_index.ISchemaCreateSpatialIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_foreign_key.ISchemaCreateForeignKeyApi
import gog.my_project.data_base_schema.api.interfaces.drop_foreign_key.ISchemaDropForeignKeyApi
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor
import gog.my_project.data_base.manager.execute.manager.QueryExecute
import gog.my_project.data_base_schema.renderer.manager.DialectSelector
import gog.my_project.data_base_schema.executor.manager.SchemaExecutor
import gog.my_project.data_base_schema.dialect.nodes.rename_column.IRenameColumnSqlDialect
import gog.my_project.data_base_schema.dialect.nodes.rename_column.RenameColumnSqlStrategy
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_operation.executor.transaction.OperationTransactionExecutor
import java.sql.ResultSet
import java.sql.SQLException
import gog.my_project.tools.scripts.StringTools

class MigrationExecutor(
    queryExecutor: IQueryExecute = QueryExecute(),
    override val dialectSelector: DialectSelector = DialectSelector()
) : IMigrationExecutor {
    private val lowLevelQueryExecutor = queryExecutor
    private var schemaExecutor = SchemaExecutor(
        queryExecutor = lowLevelQueryExecutor,
        dialectSelector = dialectSelector,
    )
    private var operationScope: OperationExecutionScope? = null
    private var transactionExecutor = OperationTransactionExecutor()

    override var queryExecutor: IQueryExecute = MigrationQueryExecute(
        delegate = lowLevelQueryExecutor,
        schemaExecutor = schemaExecutor,
        scope = null,
    )
        private set

    internal constructor(
        queryExecutor: IQueryExecute,
        dialectSelector: DialectSelector,
        transactionExecutor: OperationTransactionExecutor,
    ) : this(queryExecutor, dialectSelector) {
        this.transactionExecutor = transactionExecutor
    }

    private constructor(
        queryExecutor: IQueryExecute,
        dialectSelector: DialectSelector,
        schemaExecutor: SchemaExecutor,
        scope: OperationExecutionScope,
    ) : this(queryExecutor, dialectSelector) {
        this.schemaExecutor = schemaExecutor
        this.operationScope = scope
        this.queryExecutor = MigrationQueryExecute(queryExecutor, schemaExecutor, scope)
    }

    override fun <T> transaction(
        block: (tx: IMigrationExecutor) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        val activeScope = operationScope
        if (activeScope != null) {
            val failure = IllegalStateException("Nested migration transactions are not supported")
            activeScope.recordFailure(failure)
            return ExecuteResult.Failure(failure)
        }
        return transactionExecutor.transaction { scope ->
            val scopedExecutor = MigrationExecutor(
                lowLevelQueryExecutor,
                dialectSelector,
                schemaExecutor,
                scope,
            )
            block(scopedExecutor)
        }
    }

    /** Renders and executes the supplied Operation Schema API through the shared query scope. */
    override fun executeOut(
        schemaBuilder: ISchemaApi<*>,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        blockResult: (db: IQueryExecute, builtQuery: BuiltQuery) -> Unit,
    ) {
        val query = dialectSelector
            .select(DefaultDatabaseConfig.config.dialect)
            .render(schemaBuilder.ast)
            ?: throw IllegalStateException("rendered schema sql is null")
        val params = schemaBuilder.params
        blockQueryInfo?.invoke(
            StringTools.formatSql(query),
            params.associateTo(linkedMapOf()) { it.name to it.value },
        )
        blockResult(queryExecutor, BuiltQuery(query, params))
    }

    private fun executeSchema(
        migrationOperation: ISchemaApi<*>,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        val scope = operationScope
        if (scope == null) {
            schemaExecutor.execute(migrationOperation, blockExecute, blockQueryInfo)
        } else {
            schemaExecutor.execute(migrationOperation, scope, blockExecute, blockQueryInfo)
        }
    }

    private fun executeSchemaRendered(
        query: BuiltQuery,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) {
        val scope = operationScope
        if (scope == null) schemaExecutor.executeRendered(query, blockExecute)
        else schemaExecutor.executeRendered(query, scope, blockExecute)
    }

    override fun execute(
        queryBuilder: ISchemaCreateIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeSchema(queryBuilder, blockExecute, blockQueryInfo)
    }

    override fun execute(
        queryBuilder: ISchemaDropIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeSchema(queryBuilder, blockExecute, blockQueryInfo)
    }

    override fun execute(
        queryBuilder: ISchemaCreateUniqueIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeSchema(queryBuilder, blockExecute, blockQueryInfo)
    }

    override fun execute(
        queryBuilder: ISchemaCreateMultiColumnIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeSchema(queryBuilder, blockExecute, blockQueryInfo)
    }

    override fun execute(
        queryBuilder: ISchemaCreateFullTextIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeSchema(queryBuilder, blockExecute, blockQueryInfo)
    }

    override fun execute(
        queryBuilder: ISchemaCreateSpatialIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeSchema(queryBuilder, blockExecute, blockQueryInfo)
    }

    override fun execute(
        queryBuilder: ISchemaCreateForeignKeyApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeSchema(queryBuilder, blockExecute, blockQueryInfo)
    }

    override fun execute(
        queryBuilder: ISchemaDropForeignKeyApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeSchema(queryBuilder, blockExecute, blockQueryInfo)
    }

    override fun execute(
        queryBuilder: ISchemaModifyColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeSchema(queryBuilder, blockExecute, blockQueryInfo)
    }


    override fun execute(
        queryBuilder: ISchemaRenderCreateTableApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?
    ) {

        executeSchema(queryBuilder, blockExecute, blockQueryInfo)

    }

    override fun execute(
        queryBuilder: ISchemaDropTableApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?
    ) {
        executeSchema(queryBuilder, blockExecute, blockQueryInfo)
    }

    override fun execute(
        queryBuilder: ISchemaRenameTableApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?
    ) {
        executeSchema(queryBuilder, blockExecute, blockQueryInfo)
    }

    override fun execute(
        queryBuilder: ISchemaAddColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeSchema(queryBuilder, blockExecute, blockQueryInfo)
    }

    override fun execute(
        queryBuilder: ISchemaRenameColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        val config = DefaultDatabaseConfig.config
        val target = config.targetDatabaseVersion
        val dialect = dialectSelector.select(config.dialect, target)
        val renameDialect = dialect as? IRenameColumnSqlDialect
            ?: throw IllegalStateException("Selected dialect does not support RENAME COLUMN")
        val schemaOperation = queryBuilder

        queryExecutor.getDatabaseServerInfo { infoResult ->
            when (infoResult) {
                is ExecuteResult.Failure -> blockExecute(infoResult)
                is ExecuteResult.Success -> {
                    val info = infoResult.result
                    if (info == null) {
                        blockExecute(ExecuteResult.Failure(SQLException("Database server metadata is unavailable")))
                    } else if (target != null && target.product != info.product) {
                        blockExecute(ExecuteResult.Failure(SQLException(
                            "Configured target ${target.product} does not match detected server ${info.product}",
                        )))
                    } else {
                        val strategy = try {
                            renameDialect.renameColumnSqlStrategy
                        } catch (error: Throwable) {
                            blockExecute(ExecuteResult.Failure(error))
                            return@getDatabaseServerInfo
                        }
                        when (strategy) {
                            RenameColumnSqlStrategy.NATIVE_RENAME -> {
                                if (!supportsNativeRenameColumn(info.version)) {
                                    blockExecute(ExecuteResult.Failure(SQLException(
                                        "Native RENAME COLUMN is not supported by detected server ${info.product} " +
                                            "${info.version.major}.${info.version.minor}.${info.version.patch}",
                                    )))
                                } else {
                                    executeSchema(queryBuilder, blockExecute, blockQueryInfo)
                                }
                            }
                            RenameColumnSqlStrategy.CHANGE_COLUMN -> {
                                if (!supportsLegacyChangeColumn(info.version)) {
                                    blockExecute(ExecuteResult.Failure(SQLException(
                                        "CHANGE COLUMN is not supported for detected server ${info.product} " +
                                            "${info.version.major}.${info.version.minor}.${info.version.patch}",
                                    )))
                                } else {
                                    resolveLegacyDefinitionAndExecute(
                                        queryBuilder = schemaOperation,
                                        dialect = renameDialect,
                                        blockQueryInfo = blockQueryInfo,
                                        blockExecute = blockExecute,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun resolveLegacyDefinitionAndExecute(
        queryBuilder: ISchemaRenameColumnApi,
        dialect: IRenameColumnSqlDialect,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) {
        val table = requireNotNull(queryBuilder.ast.metadata.tableName?.trim()?.takeIf(String::isNotBlank)) {
            "RENAME COLUMN requires a non-blank table name"
        }
        val source = requireNotNull(queryBuilder.ast.metadata.name?.trim()?.takeIf(String::isNotBlank)) {
            "RENAME COLUMN requires a non-blank source column name"
        }
        val showCreate = "SHOW CREATE TABLE ${quoteMySqlIdentifier(table)}"
        reportQuery(blockQueryInfo, showCreate, mutableMapOf())
        queryExecutor.executeSelect(BuiltQuery(showCreate, mutableListOf())) { createResult ->
            when (createResult) {
                is ExecuteResult.Failure -> blockExecute(createResult)
                is ExecuteResult.Success -> {
                    val sql = try {
                        val createTableSql = createResult.result?.use { rows ->
                            if (rows.next()) rows.getString(2) else null
                        } ?: throw SQLException("SHOW CREATE TABLE returned no table definition for '$table'")
                        val definitionSuffix = MySqlCreateTableColumnDefinitionParser.definitionSuffix(
                            createTableSql = createTableSql,
                            sourceColumn = source,
                        )
                        dialect.renderLegacyRenameColumn(queryBuilder.ast, definitionSuffix)
                            ?: throw IllegalStateException("Legacy rename renderer returned no SQL")
                    } catch (error: Throwable) {
                        blockExecute(ExecuteResult.Failure(error))
                        return@executeSelect
                    }
                    executeRenameDdl(queryBuilder.params, sql, blockQueryInfo, blockExecute)
                }
            }
        }
    }

    private fun executeRenameDdl(
        params: MutableList<SqlParameter<*>>,
        sql: String,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) {
        reportQuery(blockQueryInfo, sql, params.associate { it.name to it.value }.toMutableMap())
        queryExecutor.executeTable(BuiltQuery(sql, params), blockExecute)
    }

    /** Runtime capability is derived exclusively from manager-detected server metadata. */
    private fun supportsNativeRenameColumn(version: ProductDatabaseVersion): Boolean =
        when (version.product) {
            DatabaseProduct.MYSQL -> version >= MYSQL.V8_0
            DatabaseProduct.MARIA_DB -> version >= MARIA_DB.V10_5_3
        }

    private fun supportsLegacyChangeColumn(version: ProductDatabaseVersion): Boolean =
        version.product == DatabaseProduct.MYSQL || version.product == DatabaseProduct.MARIA_DB

    private fun quoteMySqlIdentifier(identifier: String): String =
        "`${identifier.replace("`", "``")}`"

    private fun reportQuery(
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        query: String?,
        params: MutableMap<String, Any?>,
    ) {
        blockQueryInfo?.invoke(query?.let(StringTools::formatSql), params)
    }

    override fun execute(
        queryBuilder: ISchemaDropColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        val ast = queryBuilder.ast
        if (!ast.metadata.ifExists) {
            executeSchema(queryBuilder, blockExecute, blockQueryInfo)
            return
        }

        // Render first so malformed AST is rejected before any query is issued.
        val dialect = dialectSelector.select(DefaultDatabaseConfig.config.dialect)
        val ddl = dialect.render(queryBuilder.ast) ?: throw IllegalStateException("rendered sql is null")
        val tableName = requireNotNull(ast.metadata.tableName)
        val columnName = requireNotNull(ast.metadata.name)
        val checkQuery = BuiltQuery(
            "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :tableName " +
                "AND COLUMN_NAME = :columnName LIMIT 1",
            mutableListOf(
                SqlParameter.of("tableName", tableName),
                SqlParameter.of("columnName", columnName),
            ),
        )
        val paramsMap = checkQuery.params.associate { it.name to it.value }.toMutableMap()
        blockQueryInfo?.invoke(StringTools.formatSql(checkQuery.query!!), paramsMap)

        queryExecutor.executeSelect(checkQuery) { checkResult ->
            when (checkResult) {
                is ExecuteResult.Failure -> blockExecute(checkResult)
                is ExecuteResult.Success -> {
                    val exists = checkResult.result?.use(ResultSet::next) ?: false
                    if (!exists) {
                        blockExecute(ExecuteResult.Success(true))
                    } else {
                        val ddlParams = queryBuilder.params
                        blockQueryInfo?.invoke(
                            StringTools.formatSql(ddl),
                            ddlParams.associate { it.name to it.value }.toMutableMap(),
                        )
                        executeSchemaRendered(BuiltQuery(ddl, ddlParams)) { result ->
                            if (result is ExecuteResult.Failure && result.exception.isMissingMySqlDropTarget()) {
                                blockExecute(ExecuteResult.Success(true))
                            } else {
                                blockExecute(result)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Throwable.isMissingMySqlDropTarget(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is SQLException && current.errorCode in MYSQL_MISSING_DROP_TARGET_ERRORS) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private companion object {
        // MySQL 1091: requested column/key does not exist; 1146: table does not exist.
        val MYSQL_MISSING_DROP_TARGET_ERRORS = setOf(1091, 1146)
    }

}
