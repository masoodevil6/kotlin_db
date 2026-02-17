package gog.my_project.data_base.query.ast.schema.column

import gog.my_project.data_base.query.ast.interfaces.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst

class QueryColumnsAst() : IQueryColumnsAst {

    override var ColumnMethod: String?                = null;
    override var Column:   IQueryColumnsBaseAst?  = null;
    override var ColumnAlias:  String?                = null;

}