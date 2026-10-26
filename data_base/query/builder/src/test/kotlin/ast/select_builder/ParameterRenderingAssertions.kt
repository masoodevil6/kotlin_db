package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import gog.my_project.data_base.query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.QueryParameterRendering
import kotlin.test.assertIs

/** Reads bindings from the same renderer result that emitted the placeholders. */
internal fun renderedParameterResult(
    ast: IQueryAst<*>,
    dialect: ISqlDialect = MySqlDialect(),
): IdentityAwareQueryRenderResult = assertIs(
    when (ast) {
        is IQueryRenderSelectAst -> QueryParameterRendering.render(ast, dialect)
        else -> QueryParameterRendering.renderDml(ast, dialect)
    },
)
