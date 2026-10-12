package gog.my_project.data_base.query.ast.interfaces.select_interface.table

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst

interface IQueryTableAst : IQueryAst  {

    var table :      String? ;
    var tableAlias : String? ;

    var cte :        String? ;
    var cteAlias :   String? ;

    /** Inline SELECT source used as a derived table in SELECT FROM. */
    var derivedQuery: IQueryRenderSelectAst?

}
