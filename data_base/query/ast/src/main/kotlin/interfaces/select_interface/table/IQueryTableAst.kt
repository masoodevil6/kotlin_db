package gog.my_project.data_base.query.ast.interfaces.select_interface.table
import gog.my_project.data_base.query.ast.metadata.select.table.QueryTableAstMetadata

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst

interface IQueryTableAst : IQueryAst<QueryTableAstMetadata>  {

    var derivedQuery: IQueryRenderSelectAst?

}
