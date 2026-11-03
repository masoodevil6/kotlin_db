package gog.my_project.data_base.migration.executor.interfaces

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.api.interfaces.add_column.ISchemaAddColumnApi
import gog.my_project.data_base_schema.api.interfaces.create_foreign_key.ISchemaCreateForeignKeyApi
import gog.my_project.data_base_schema.api.interfaces.create_full_text_index.ISchemaCreateFullTextIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_index.ISchemaCreateIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_multi_column_index.ISchemaCreateMultiColumnIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_spatial_index.ISchemaCreateSpatialIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableApi
import gog.my_project.data_base_schema.api.interfaces.create_unique_index.ISchemaCreateUniqueIndexApi
import gog.my_project.data_base_schema.api.interfaces.drop_column.ISchemaDropColumnApi
import gog.my_project.data_base_schema.api.interfaces.drop_foreign_key.ISchemaDropForeignKeyApi
import gog.my_project.data_base_schema.api.interfaces.drop_index.ISchemaDropIndexApi
import gog.my_project.data_base_schema.api.interfaces.drop_table.ISchemaDropTableApi
import gog.my_project.data_base_schema.api.interfaces.modify_column.ISchemaModifyColumnApi
import gog.my_project.data_base_schema.api.interfaces.rename_column.ISchemaRenameColumnApi
import gog.my_project.data_base_schema.api.interfaces.rename_table.ISchemaRenameTableApi
import gog.my_project.data_base_schema.renderer.manager.DialectSelector
import gog.my_project.tools.scripts.StringTools


interface IMigrationExecutor {

    val queryExecutor: IQueryExecute
    val dialectSelector: DialectSelector
    @Suppress("DEPRECATION")
    val databaseContext: DatabaseContext
        get() = DatabaseContext.legacyDefault()

    /** Executes migration operations inside one shared Operation transaction when supported. */
    fun <T> transaction(block: (tx: IMigrationExecutor) -> ExecuteResult<T>): ExecuteResult<T> =
        ExecuteResult.Failure(
            UnsupportedOperationException(
                "${this::class.qualifiedName} does not support transaction-scoped migration execution",
            ),
        )

    //// [EXECUTE]: create table | alter table | drop table
    fun execute(
        queryBuilder: ISchemaRenderCreateTableApi,
        blockExecute:    (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo:  ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: ISchemaDropTableApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: ISchemaRenameTableApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: ISchemaAddColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: ISchemaRenameColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: ISchemaDropColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: ISchemaModifyColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: ISchemaCreateIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: ISchemaDropIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: ISchemaCreateUniqueIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: ISchemaCreateMultiColumnIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: ISchemaCreateFullTextIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: ISchemaCreateSpatialIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: ISchemaCreateForeignKeyApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
    )

    fun execute(
        queryBuilder: ISchemaDropForeignKeyApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
    )



    //// [TOOLS]
    fun executeOut(
        schemaBuilder: ISchemaApi<*>,
        blockQueryInfo:  ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null,
        blockResult:     (db: IQueryExecute ,builtQuery: BuiltQuery) -> Unit
    ){
        val db = queryExecutor

        val platform = databaseContext.platformConfig
        val dialect = dialectSelector.select(platform.dialect, platform.targetDatabaseVersion)

        val query = dialect.render(schemaBuilder.ast) ?: throw IllegalStateException("rendered sql is null")

        if (blockQueryInfo != null){
            val paramsMap: MutableMap<String , Any?> = mutableMapOf()
            val queryString = StringTools.formatSql(query)

            blockQueryInfo(queryString , paramsMap)
        }

        blockResult(db, BuiltQuery(query, mutableListOf()))
    }

}
