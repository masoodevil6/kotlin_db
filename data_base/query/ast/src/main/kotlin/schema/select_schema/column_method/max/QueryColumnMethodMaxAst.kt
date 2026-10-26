package gog.my_project.data_base.query.ast.schema.select_schema.column_method.max

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.max.IQueryColumnMethodMaxAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_over.IQueryColumnOverAst
import gog.my_project.data_base.query.ast.metadata.select.column_method.max.QueryColumnMethodMaxAstMetadata

class QueryColumnMethodMaxAst(
    override var metadata: QueryColumnMethodMaxAstMetadata = QueryColumnMethodMaxAstMetadata()
) : IQueryColumnMethodMaxAst {

    override var Column:   IQueryColumnsBaseAst?   = null;
    override var Over:     IQueryColumnOverAst? = null;
}