package gog.my_project.data_base.migration.executor.internal.system_migration

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.renderer.manager.DialectSelector
import gog.my_project.data_base.query.ast.interfaces.delete_interface.query_render_delete.IQueryRenderDeleteAst
import gog.my_project.data_base.query.ast.interfaces.insert_interface.query_render_insert.IQueryRenderInsertAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.interfaces.update_interface.query_render_update.IQueryRenderUpdateAst
import gog.my_project.data_base.query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.LegacyQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.QueryParameterRendering

/** Renders a Query API through the configured query dialect and wraps it for the existing manager. */
internal fun <Ast : IQueryAst<*>, Api : IQueryApi<Ast>> Api.toBuiltQuery(): BuiltQuery {
    val dialect = DialectSelector().select(DefaultDatabaseConfig.config.dialect)
    val rendered = when (val queryAst = ast) {
        is IQueryRenderSelectAst -> QueryParameterRendering.render(queryAst, dialect)
        is IQueryRenderInsertAst, is IQueryRenderUpdateAst, is IQueryRenderDeleteAst ->
            QueryParameterRendering.renderDml(queryAst, dialect)
        else -> return BuiltQuery(
            dialect.render(queryAst) ?: throw IllegalStateException("rendered SQL is null"),
            mutableListOf(),
        )
    }
    return when (rendered) {
        is LegacyQueryRenderResult -> BuiltQuery(
            rendered.sql ?: throw IllegalStateException("rendered SQL is null"),
            mutableListOf(),
        )
        is IdentityAwareQueryRenderResult -> BuiltQuery(
            rendered.displaySql,
            rendered.parameterOccurrences.toMutableList(),
        )
    }
}
