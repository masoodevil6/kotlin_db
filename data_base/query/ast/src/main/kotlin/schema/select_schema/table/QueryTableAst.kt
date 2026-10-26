package gog.my_project.data_base.query.ast.schema.select_schema_ast.table
import gog.my_project.data_base.query.ast.metadata.select.table.QueryTableAstMetadata

import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst


class QueryTableAst(
    override var metadata: QueryTableAstMetadata = QueryTableAstMetadata()
) : IQueryTableAst {

    override var derivedQuery: IQueryRenderSelectAst? = null

}
