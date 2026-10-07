package gog.my_project.data_base.query.api.interfaces.relations

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst

/** A built query payload that can be exposed as a composable relation. */
interface QueryDefinition {
    val ast: IQueryRenderSelectAst
    val params: List<SqlParameter<*>>
}
