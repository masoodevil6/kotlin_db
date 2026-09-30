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
import gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableApi
import gog.my_project.data_base.migration.api.interfaces.add_column.IMigrationAddColumnApi
import gog.my_project.data_base.migration.api.interfaces.drop_column.IMigrationDropColumnApi
import gog.my_project.data_base.migration.api.interfaces.drop_table.IMigrationDropTableApi
import gog.my_project.data_base.migration.api.interfaces.rename_table.IMigrationRenameTableApi
import gog.my_project.data_base.migration.api.interfaces.rename_column.IMigrationRenameColumnApi
import gog.my_project.data_base.migration.api.interfaces.modify_column.IMigrationModifyColumnApi
import gog.my_project.data_base.migration.api.interfaces.create_index.IMigrationCreateIndexApi
import gog.my_project.data_base.migration.api.interfaces.drop_index.IMigrationDropIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_unique_index.IMigrationCreateUniqueIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_multi_column_index.IMigrationCreateMultiColumnIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_full_text_index.IMigrationCreateFullTextIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_spatial_index.IMigrationCreateSpatialIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_foreign_key.IMigrationCreateForeignKeyApi
import gog.my_project.data_base.migration.api.interfaces.drop_foreign_key.IMigrationDropForeignKeyApi
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor
import gog.my_project.data_base.manager.execute.manager.QueryExecute
import gog.my_project.data_base.migration.renderer.manager.DialectSelector
import gog.my_project.data_base.migration.dialect.nodes.rename_column.IRenameColumnSqlDialect
import gog.my_project.data_base.migration.dialect.nodes.rename_column.RenameColumnSqlStrategy
import java.sql.ResultSet
import java.sql.SQLException
import gog.my_project.tools.scripts.StringTools

class MigrationExecutor(
    override val queryExecutor: IQueryExecute = QueryExecute(),
    override val dialectSelector: DialectSelector = DialectSelector()
) : IMigrationExecutor {

    override fun execute(
        queryBuilder: IMigrationCreateIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeOut(
            migrationBuilder = queryBuilder,
            blockQueryInfo = blockQueryInfo,
            blockResult = { db, builtQuery -> db.executeTable(builtQuery, blockExecute) },
        )
    }

    override fun execute(
        queryBuilder: IMigrationDropIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeOut(
            migrationBuilder = queryBuilder,
            blockQueryInfo = blockQueryInfo,
            blockResult = { db, builtQuery -> db.executeTable(builtQuery, blockExecute) },
        )
    }

    override fun execute(
        queryBuilder: IMigrationCreateUniqueIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeOut(
            migrationBuilder = queryBuilder,
            blockQueryInfo = blockQueryInfo,
            blockResult = { db, builtQuery -> db.executeTable(builtQuery, blockExecute) },
        )
    }

    override fun execute(
        queryBuilder: IMigrationCreateMultiColumnIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeOut(
            migrationBuilder = queryBuilder,
            blockQueryInfo = blockQueryInfo,
            blockResult = { db, builtQuery -> db.executeTable(builtQuery, blockExecute) },
        )
    }

    override fun execute(
        queryBuilder: IMigrationCreateFullTextIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeOut(
            migrationBuilder = queryBuilder,
            blockQueryInfo = blockQueryInfo,
            blockResult = { db, builtQuery -> db.executeTable(builtQuery, blockExecute) },
        )
    }

    override fun execute(
        queryBuilder: IMigrationCreateSpatialIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeOut(
            migrationBuilder = queryBuilder,
            blockQueryInfo = blockQueryInfo,
            blockResult = { db, builtQuery -> db.executeTable(builtQuery, blockExecute) },
        )
    }

    override fun execute(
        queryBuilder: IMigrationCreateForeignKeyApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeOut(
            migrationBuilder = queryBuilder,
            blockQueryInfo = blockQueryInfo,
            blockResult = { db, builtQuery -> db.executeTable(builtQuery, blockExecute) },
        )
    }

    override fun execute(
        queryBuilder: IMigrationDropForeignKeyApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeOut(
            migrationBuilder = queryBuilder,
            blockQueryInfo = blockQueryInfo,
            blockResult = { db, builtQuery -> db.executeTable(builtQuery, blockExecute) },
        )
    }

    override fun execute(
        queryBuilder: IMigrationModifyColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeOut(
            migrationBuilder = queryBuilder,
            blockQueryInfo = blockQueryInfo,
            blockResult = { db, builtQuery -> db.executeTable(builtQuery, blockExecute) },
        )
    }


    override fun execute(
        queryBuilder: IMigrationRenderCreateTableApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?
    ) {

        return executeOut(
            migrationBuilder =    queryBuilder,
            blockQueryInfo =  blockQueryInfo,
            blockResult=   {
                    db: IQueryExecute, builtQuery: BuiltQuery->
                db.executeTable(
                    builtQuery =   builtQuery,
                    blockExecute = blockExecute
                )
            }
        );

    }

    override fun execute(
        queryBuilder: IMigrationDropTableApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?
    ) {
        executeOut(
            migrationBuilder = queryBuilder,
            blockQueryInfo = blockQueryInfo,
            blockResult = { db, builtQuery ->
                db.executeTable(
                    builtQuery = builtQuery,
                    blockExecute = blockExecute,
                )
            },
        )
    }

    override fun execute(
        queryBuilder: IMigrationRenameTableApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?
    ) {
        executeOut(
            migrationBuilder = queryBuilder,
            blockQueryInfo = blockQueryInfo,
            blockResult = { db, builtQuery ->
                db.executeTable(
                    builtQuery = builtQuery,
                    blockExecute = blockExecute,
                )
            },
        )
    }

    override fun execute(
        queryBuilder: IMigrationAddColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        executeOut(
            migrationBuilder = queryBuilder,
            blockQueryInfo = blockQueryInfo,
            blockResult = { db, builtQuery ->
                db.executeTable(
                    builtQuery = builtQuery,
                    blockExecute = blockExecute,
                )
            },
        )
    }

    override fun execute(
        queryBuilder: IMigrationRenameColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        val config = DefaultDatabaseConfig.config
        val target = config.targetDatabaseVersion
        val dialect = dialectSelector.select(config.dialect, target)
        val renameDialect = dialect as? IRenameColumnSqlDialect
            ?: throw IllegalStateException("Selected dialect does not support RENAME COLUMN")

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
                                    val sql = dialect.render(queryBuilder.ast)
                                        ?: throw IllegalStateException("rendered sql is null")
                                    executeRenameDdl(queryBuilder.params, sql, blockQueryInfo, blockExecute)
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
                                        queryBuilder = queryBuilder,
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
        queryBuilder: IMigrationRenameColumnApi,
        dialect: IRenameColumnSqlDialect,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) {
        val table = requireNotNull(queryBuilder.ast.tableName?.trim()?.takeIf(String::isNotBlank)) {
            "RENAME COLUMN requires a non-blank table name"
        }
        val source = requireNotNull(queryBuilder.ast.name?.trim()?.takeIf(String::isNotBlank)) {
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
        queryBuilder: IMigrationDropColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        val ast = queryBuilder.ast
        if (!ast.ifExists) {
            executeOut(
                migrationBuilder = queryBuilder,
                blockQueryInfo = blockQueryInfo,
                blockResult = { db, builtQuery -> db.executeTable(builtQuery, blockExecute) },
            )
            return
        }

        // Render first so malformed AST is rejected before any query is issued.
        val dialect = dialectSelector.select(DefaultDatabaseConfig.config.dialect)
        val ddl = dialect.render(ast) ?: throw IllegalStateException("rendered sql is null")
        val tableName = requireNotNull(ast.tableName)
        val columnName = requireNotNull(ast.name)
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
                        queryExecutor.executeTable(BuiltQuery(ddl, ddlParams)) { result ->
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
