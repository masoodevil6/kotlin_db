package gog.my_project.data_base.query.ast.interfaces.table

import gog.my_project.data_base.query.ast.interfaces.IQueryAst

interface IQueryTableAst : IQueryAst  {

    var table :      String? ;
    var tableAlias : String? ;

    var cte :        String? ;
    var cteAlias :   String? ;

}