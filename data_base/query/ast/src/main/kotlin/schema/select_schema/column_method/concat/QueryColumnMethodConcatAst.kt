package gog.my_project.data_base.query.ast.schema.select_schema.column_method.concat

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.concat.IQueryColumnMethodConcatAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.concat.IQueryColumnMethodConcatAstPart
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_over.IQueryColumnOverAst
import gog.my_project.data_base.query.ast.metadata.select.column_method.concat.QueryColumnMethodConcatAstMetadata

class QueryColumnMethodConcatAst(
    override var metadata: QueryColumnMethodConcatAstMetadata = QueryColumnMethodConcatAstMetadata(),
) : IQueryColumnMethodConcatAst {

    override val parts: MutableList<IQueryColumnMethodConcatAstPart> = mutableListOf()
    override var Over: IQueryColumnOverAst? = null;
}