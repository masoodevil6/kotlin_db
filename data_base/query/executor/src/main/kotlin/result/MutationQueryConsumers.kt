package gog.my_project.data_base.query.executer.result

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.renderer.manager.DialectSelector
import gog.my_project.data_base.query.renderer.parameters.LegacyQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.QueryParameterRendering

class InsertQueryConsumer internal constructor(
    private val query: IQueryRenderInsertApi,
    private val executor: IQueryBuilderExecutor,
) {
    fun execute(): ExecuteResult<Long> = captureMutationResult { callback ->
        executor.execute(query, blockExecute = callback)
    }

    fun sql(): BuiltQuery = mutationBuiltQuery(query.ast)

    fun sql(block: (BuiltQuery) -> Unit): InsertQueryConsumer {
        block(sql())
        return this
    }
}

class UpdateQueryConsumer internal constructor(
    private val query: IQueryRenderUpdateApi,
    private val executor: IQueryBuilderExecutor,
) {
    fun execute(): ExecuteResult<Int> = captureMutationResult { callback ->
        executor.execute(query, blockExecute = callback)
    }

    fun sql(): BuiltQuery = mutationBuiltQuery(query.ast)

    fun sql(block: (BuiltQuery) -> Unit): UpdateQueryConsumer {
        block(sql())
        return this
    }
}

class DeleteQueryConsumer internal constructor(
    private val query: IQueryRenderDeleteApi,
    private val executor: IQueryBuilderExecutor,
) {
    fun execute(): ExecuteResult<Int> = captureMutationResult { callback ->
        executor.execute(query, blockExecute = callback)
    }

    fun sql(): BuiltQuery = mutationBuiltQuery(query.ast)

    fun sql(block: (BuiltQuery) -> Unit): DeleteQueryConsumer {
        block(sql())
        return this
    }
}

/** Uses one render result for named SQL and the corresponding parameter bindings. */
private fun mutationBuiltQuery(
    ast: IQueryAst<*>,
): BuiltQuery {
    val dialect = DialectSelector().select(DefaultDatabaseConfig.config.dialect)
    return when (val rendered = QueryParameterRendering.renderDml(ast, dialect)) {
        is LegacyQueryRenderResult -> BuiltQuery(
            requireNotNull(rendered.sql) { "rendered sql is null" },
            mutableListOf(),
        )
        is IdentityAwareQueryRenderResult -> BuiltQuery(
            rendered.displaySql,
            rendered.parameterOccurrences.toMutableList(),
        )
    }
}

private fun <T> captureMutationResult(
    operation: (callback: (ExecuteResult<T>) -> Unit) -> Unit,
): ExecuteResult<T> {
    var result: ExecuteResult<T>? = null
    return try {
        operation { delivered ->
            check(result == null) { "Mutation execution delivered more than one result" }
            result = delivered
        }
        result ?: ExecuteResult.Failure(
            IllegalStateException("Mutation execution did not deliver a synchronous result"),
        )
    } catch (failure: Throwable) {
        ExecuteResult.Failure(failure)
    }
}
