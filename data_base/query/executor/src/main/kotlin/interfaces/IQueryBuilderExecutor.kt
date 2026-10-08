package gog.my_project.data_base.query.executer.interfaces

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.manager.QueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.requireResultOutputAliases
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.executer.result.QueryRow
import gog.my_project.data_base.query.executer.result.QueryConsumer
import gog.my_project.data_base.query.executer.result.QueryBuilderSelection
import gog.my_project.data_base.query.executer.result.InsertQueryConsumer
import gog.my_project.data_base.query.executer.result.UpdateQueryConsumer
import gog.my_project.data_base.query.executer.result.DeleteQueryConsumer
import gog.my_project.data_base.query.renderer.manager.DialectSelector
import gog.my_project.tools.scripts.StringTools
import java.sql.ResultSet

interface IQueryBuilderExecutor  {

    /** Builds a SELECT query and binds this executor to its fluent consumer. */
    fun queryBuilderSelect(
        block: (IQueryRenderSelectApi) -> Unit,
    ): QueryConsumer {
        val query = QueryRenderSelectBuilder()
        block(query)
        return QueryConsumer(query, this)
    }

    /** Binds an already constructed SELECT query to this executor. */
    fun queryBuilder(query: IQueryRenderSelectApi): QueryConsumer =
        QueryConsumer(query, this)

    /** Binds an already constructed INSERT query to this executor. */
    fun queryBuilder(query: IQueryRenderInsertApi): InsertQueryConsumer =
        InsertQueryConsumer(query, this)

    /** Binds an already constructed UPDATE query to this executor. */
    fun queryBuilder(query: IQueryRenderUpdateApi): UpdateQueryConsumer =
        UpdateQueryConsumer(query, this)

    /** Binds an already constructed DELETE query to this executor. */
    fun queryBuilder(query: IQueryRenderDeleteApi): DeleteQueryConsumer =
        DeleteQueryConsumer(query, this)

    /** Builds an operation-specific query through the unified queryBuilder entry point. */
    fun <T> queryBuilder(block: QueryBuilderOperationBlock<T>): T =
        block.build(QueryBuilderSelection(this))

    /** Returns rendered SQL without interpolating or changing the query's parameters. */
    fun sql(queryBuilder: IQueryRenderSelectApi): String = renderSql(queryBuilder)

    fun sql(queryBuilder: IQueryRenderInsertApi): String = renderSql(queryBuilder)

    fun sql(queryBuilder: IQueryRenderUpdateApi): String = renderSql(queryBuilder)

    fun sql(queryBuilder: IQueryRenderDeleteApi): String = renderSql(queryBuilder)

    /** Materializes the first row while the low-level ResultSet is still live. */
    fun first(
        queryBuilder: IQueryRenderSelectApi,
        blockExecute: (ExecuteResult<QueryRow>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
    ) {
        val outputColumns = try {
            queryBuilder.requireResultOutputAliases()
            val columns = queryBuilder.ast.select!!.columns
            columns.forEachIndexed { index, column ->
                require(column.ExecutionType != null) {
                    "SELECT output at index $index ('${column.ColumnAlias}') must declare an execution DataType"
                }
            }
            columns
        } catch (failure: Throwable) {
            blockExecute(ExecuteResult.Failure(failure))
            return
        }

        execute(
            queryBuilder = queryBuilder,
            blockExecute = { result ->
                val materialized: ExecuteResult<QueryRow> = when (result) {
                    is ExecuteResult.Failure -> result
                    is ExecuteResult.Success -> {
                        val resultSet = result.result
                        if (resultSet == null) {
                            ExecuteResult.Failure(IllegalStateException("SELECT completed without a ResultSet"))
                        } else {
                            try {
                                resultSet.use { rows ->
                                    if (rows.next()) {
                                        ExecuteResult.Success(materializeQueryRow(rows, outputColumns))
                                    } else {
                                        ExecuteResult.Success(null)
                                    }
                                }
                            } catch (failure: Throwable) {
                                ExecuteResult.Failure(failure)
                            }
                        }
                    }
                }
                blockExecute(materialized)
            },
            blockQueryInfo = blockQueryInfo,
        )
    }

    /** Materializes every row while the low-level ResultSet is still live. */
    fun get(
        queryBuilder: IQueryRenderSelectApi,
        blockExecute: (ExecuteResult<List<QueryRow>>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
    ) {
        val outputColumns = try {
            queryBuilder.requireResultOutputAliases()
            val columns = queryBuilder.ast.select!!.columns
            columns.forEachIndexed { index, column ->
                require(column.ExecutionType != null) {
                    "SELECT output at index $index ('${column.ColumnAlias}') must declare an execution DataType"
                }
            }
            columns
        } catch (failure: Throwable) {
            blockExecute(ExecuteResult.Failure(failure))
            return
        }

        execute(
            queryBuilder = queryBuilder,
            blockExecute = { result ->
                val materialized: ExecuteResult<List<QueryRow>> = when (result) {
                    is ExecuteResult.Failure -> result
                    is ExecuteResult.Success -> {
                        val resultSet = result.result
                        if (resultSet == null) {
                            ExecuteResult.Failure(IllegalStateException("SELECT completed without a ResultSet"))
                        } else {
                            try {
                                resultSet.use { rows ->
                                    val values = mutableListOf<QueryRow>()
                                    while (rows.next()) {
                                        values += materializeQueryRow(rows, outputColumns)
                                    }
                                    ExecuteResult.Success(values)
                                }
                            } catch (failure: Throwable) {
                                ExecuteResult.Failure(failure)
                            }
                        }
                    }
                }
                blockExecute(materialized)
            },
            blockQueryInfo = blockQueryInfo,
        )
    }

    //// select
    fun execute(
        queryBuilder:    IQueryRenderSelectApi,
        blockExecute:    (ExecuteResult<ResultSet>) -> Unit,
        blockQueryInfo:  ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null
    )

    //// insert
    fun execute(
        queryBuilder:     IQueryRenderInsertApi,
        blockExecute:     (ExecuteResult<Long>) -> Unit,
        blockQueryInfo:   ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null
    )

    //// update
    fun execute(
        queryBuilder:     IQueryRenderUpdateApi,
        blockExecute:     (ExecuteResult<Int>) -> Unit,
        blockQueryInfo:   ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null
    )

    //// update
    fun execute(
        queryBuilder:     IQueryRenderDeleteApi,
        blockExecute:     (ExecuteResult<Int>) -> Unit,
        blockQueryInfo:   ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null
    )


    fun <Ast: IQueryAst,Api :IQueryApi<Ast> > executeOut(
        queryBuilder:    Api,
        blockQueryInfo:  ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null,
        blockResult:     (db: IQueryExecute ,builtQuery: BuiltQuery) -> Unit
    ){
        val db = QueryExecute()

        val dialect  = DialectSelector().select(DefaultDatabaseConfig.config.dialect)

        val query = dialect.render(queryBuilder.ast) ?: throw IllegalStateException("rendered sql is null")

        val params: MutableList<SqlParameter<*>> = queryBuilder.params;

        if (blockQueryInfo != null){
            val paramsMap: MutableMap<String , Any?> = params.associate { it -> it.name to it.value }.toMutableMap()
            val queryString = StringTools.formatSql(query)

            blockQueryInfo(queryString , paramsMap)
        }

        blockResult(db , BuiltQuery(query, params));
    }

}

private fun <Ast : IQueryAst> renderSql(queryBuilder: IQueryApi<Ast>): String {
    val dialect = DialectSelector().select(DefaultDatabaseConfig.config.dialect)
    return dialect.render(queryBuilder.ast)
        ?: throw IllegalStateException("rendered sql is null")
}

private fun materializeQueryRow(
    resultSet: ResultSet,
    outputColumns: List<IQueryColumnsAst>,
): QueryRow {
    val metadata = resultSet.metaData
    check(metadata.columnCount == outputColumns.size) {
        "ResultSet column count does not match SELECT output count"
    }

    val values = (1..metadata.columnCount).map { index ->
        val outputColumn = outputColumns[index - 1]
        val expectedAlias = requireNotNull(outputColumn.ColumnAlias)
        val label = metadata.getColumnLabel(index)
        check(label.isNotBlank()) { "ResultSet column $index has no SQL output alias" }
        check(label == expectedAlias) {
            "ResultSet column label '$label' does not match SELECT output alias '$expectedAlias'"
        }
        val value = when (requireNotNull(outputColumn.ExecutionType)) {
            DataType.LONG -> resultSet.getLong(index).let { if (resultSet.wasNull()) null else it }
            DataType.INT -> resultSet.getInt(index).let { if (resultSet.wasNull()) null else it }
            DataType.STRING -> resultSet.getString(index)
        }
        label to value
    }
    val propertyAliases = outputColumns.mapNotNull { outputColumn ->
        val property = outputColumn.PropertyReference ?: return@mapNotNull null
        property to requireNotNull(outputColumn.ColumnAlias)
    }
    return QueryRow(values, propertyAliases)
}
