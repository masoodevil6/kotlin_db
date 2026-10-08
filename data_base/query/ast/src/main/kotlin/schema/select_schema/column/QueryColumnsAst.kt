package gog.my_project.data_base.query.ast.schema.select_schema.column

import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.enums.DataType
import kotlin.reflect.KProperty1

class QueryColumnsAst() : IQueryColumnsAst {

    override var ColumnMethod: String?                = null;
    override var Column:       IQueryColumnsBaseAst?  = null;
    override var ColumnAlias:  String?                = null;
    override var ExecutionType: DataType?              = null;
    override var PropertyReference: KProperty1<*, *>?   = null;

}
