package gog.my_project.data_base.query.ast.interfaces.select_interface.table

import gog.my_project.data_base.query.ast.interfaces.IQueryAst

interface IQueryTableAst : IQueryAst  {

    var table :      String? ;
    var tableAlias : String? ;

    var cte :        String? ;
    var cteAlias :   String? ;

}