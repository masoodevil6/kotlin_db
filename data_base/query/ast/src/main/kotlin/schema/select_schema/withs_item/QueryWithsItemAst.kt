package gog.my_project.data_base.query.ast.schema.select_schema_ast.withs_item

import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs_item.IQueryWithsItemAst


class QueryWithsItemAst :
    IQueryWithsItemAst {

    override var withName : String?                  = null;
    override var withBody:  IQueryRenderSelectAst?   = null;

}