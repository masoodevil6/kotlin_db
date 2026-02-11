package gog.my_project.data_base.query.ast.interfaces.withs_item

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.query_render_select.IQueryRenderSelectAst

interface IQueryWithsItemAst : IQueryAst {

    var withName : String?     ;
    var withBody: IQueryRenderSelectAst?;

}