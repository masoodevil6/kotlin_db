package gog.my_project.data_base.query.api.interfaces.relations

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst

/**
 * A composable CTE definition whose SELECT aliases are its SQL-visible outputs.
 * Implementations use reference identity; [name] is only its SQL CTE name.
 */
interface Cte {
    val name: String
    val definition: IQueryApi<IQueryRenderSelectAst>

    /** CTEs consumed by this definition, in source dependency order. */
    val dependencies: List<Cte> get() = emptyList()
}
