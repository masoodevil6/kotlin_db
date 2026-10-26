package gog.my_project.data_base.query.ast.interfaces.select_interface.column
import gog.my_project.data_base.query.ast.metadata.select.column.QueryColumnsAstMetadata

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.concat.IQueryColumnMethodConcatAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.avg.IQueryColumnMethodAvgAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.count.IQueryColumnMethodCountAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.max.IQueryColumnMethodMaxAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.min.IQueryColumnMethodMinAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.sum.IQueryColumnMethodSumAst
import kotlin.reflect.KProperty1

interface IQueryColumnsAst : IQueryAst<QueryColumnsAstMetadata> {

    var Column:                    IQueryColumnsBaseAst?
    var ColumnMethodCount:         IQueryColumnMethodCountAst?
    var ColumnMethodConcat:        IQueryColumnMethodConcatAst?
    var ColumnMethodMin:           IQueryColumnMethodMinAst?
    var ColumnMethodMax:           IQueryColumnMethodMaxAst?
    var ColumnMethodSum:           IQueryColumnMethodSumAst?
    var ColumnMethodAvg:           IQueryColumnMethodAvgAst?

}
