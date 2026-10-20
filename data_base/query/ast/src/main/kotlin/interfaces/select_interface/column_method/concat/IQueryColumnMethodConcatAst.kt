package gog.my_project.data_base.query.ast.interfaces.select_interface.column_method

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.metadata.select.column_method.QueryColumnMethodConcatAstMetadata

interface IQueryColumnMethodConcatAst : IQueryAst<QueryColumnMethodConcatAstMetadata>  {
    
    val parts: MutableList<IQueryColumnMethodConcatAstPart>

}