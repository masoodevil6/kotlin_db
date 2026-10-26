package gog.my_project.data_base.query.ast.schema.select_schema.column

import gog.my_project.data_base.query.ast.metadata.select.column.QueryColumnsAstMetadata
import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.concat.IQueryColumnMethodConcatAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.avg.IQueryColumnMethodAvgAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.count.IQueryColumnMethodCountAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.max.IQueryColumnMethodMaxAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.min.IQueryColumnMethodMinAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.sum.IQueryColumnMethodSumAst

class QueryColumnsAst(
    override var metadata: QueryColumnsAstMetadata = QueryColumnsAstMetadata()
) : IQueryColumnsAst {

    override var Column:              IQueryColumnsBaseAst?        = null;

    override var ColumnMethodCount:   IQueryColumnMethodCountAst? = null;
    override var ColumnMethodConcat:  IQueryColumnMethodConcatAst? = null;
    override var ColumnMethodMin:     IQueryColumnMethodMinAst?    = null;
    override var ColumnMethodMax:     IQueryColumnMethodMaxAst?    = null;
    override var ColumnMethodSum:     IQueryColumnMethodSumAst?    = null;
    override var ColumnMethodAvg:     IQueryColumnMethodAvgAst?    = null;

}
