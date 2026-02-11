package gog.my_project.data_base.query.ast.schema.withs_item

import gog.my_project.data_base.query.ast.interfaces.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.interfaces.withs_item.IQueryWithsItemAst

class QueryWithsItemAst : IQueryWithsItemAst {

    override var withName : String?                  = null;
    override var withBody:  IQueryRenderSelectAst?   = null;

}