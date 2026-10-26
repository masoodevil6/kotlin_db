package gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.sum

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_over.IQueryColumnOverAst
import gog.my_project.data_base.query.ast.metadata.select.column_method.sum.QueryColumnMethodSumAstMetadata

interface IQueryColumnMethodSumAst : IQueryAst<QueryColumnMethodSumAstMetadata> {

    var Column:     IQueryColumnsBaseAst?

    var Over:       IQueryColumnOverAst?

}
