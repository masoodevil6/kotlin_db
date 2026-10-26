package gog.my_project.data_base.query.ast.interfaces.select_interface.withs_item
import gog.my_project.data_base.query.ast.metadata.select.withs_item.QueryWithsItemAstMetadata

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst

interface IQueryWithsItemAst : IQueryAst<QueryWithsItemAstMetadata> {

    var withBody:  IQueryRenderSelectAst?

}