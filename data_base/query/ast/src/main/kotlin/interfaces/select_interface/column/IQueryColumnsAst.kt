package gog.my_project.data_base.query.ast.interfaces.select_interface.column

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.enums.DataType
import kotlin.reflect.KProperty1

interface IQueryColumnsAst : IQueryAst {

    var ColumnMethod: String? ;
    var Column:       IQueryColumnsBaseAst?;
    var ColumnAlias:  String?;
    var ExecutionType: DataType?;
    var PropertyReference: KProperty1<*, *>?;

}
