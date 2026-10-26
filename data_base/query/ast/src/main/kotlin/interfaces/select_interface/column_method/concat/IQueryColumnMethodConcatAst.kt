package gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.concat

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_over.IQueryColumnOverAst
import gog.my_project.data_base.query.ast.metadata.select.column_method.concat.QueryColumnMethodConcatAstMetadata

interface IQueryColumnMethodConcatAst : IQueryAst<QueryColumnMethodConcatAstMetadata>  {

    val parts: MutableList<IQueryColumnMethodConcatAstPart>

    var Over:       IQueryColumnOverAst?
}