package gog.my_project.data_base.query.ast.interfaces.update_interface.column_update

import gog.my_project.data_base.query.ast.interfaces.IQueryAst

interface IQueryColumnUpdateAst : IQueryAst  {

    var columnAlias:  String? ;
    var columnName:   String? ;
    var columnTag:    String? ;

}