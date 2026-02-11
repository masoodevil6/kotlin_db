package gog.my_project.data_base.query.ast.interfaces.column

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst

interface IQueryColumnsAst : IQueryAst {

    var ColumnMethod: String? ;
    var ColumnName:   IQueryColumnsBaseAst?;
    var ColumnAlias:  String?;

}