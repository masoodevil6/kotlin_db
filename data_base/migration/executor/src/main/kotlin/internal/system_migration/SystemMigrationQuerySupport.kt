package gog.my_project.data_base.migration.executor.internal.system_migration

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.renderer.manager.DialectSelector

/** Renders a Query API through the configured query dialect and wraps it for the existing manager. */
internal fun <Ast : IQueryAst, Api : IQueryApi<Ast>> Api.toBuiltQuery(): BuiltQuery {
    val dialect = DialectSelector().select(DefaultDatabaseConfig.config.dialect)
    val query = dialect.render(ast)
        ?: throw IllegalStateException("rendered SQL is null")
    return BuiltQuery(query, params)
}
