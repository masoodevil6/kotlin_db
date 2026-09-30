package gog.my_project.data_base.query.ast.interfaces.select_interface.column

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst

interface IQueryColumnsAst : IQueryAst {

    var ColumnMethod: String? ;
    var Column:       IQueryColumnsBaseAst?;
    var ColumnAlias:  String?;

}