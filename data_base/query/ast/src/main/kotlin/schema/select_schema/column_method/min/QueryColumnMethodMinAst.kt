package gog.my_project.data_base.query.ast.schema.select_schema.column_method.min

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.min.IQueryColumnMethodMinAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_over.IQueryColumnOverAst
import gog.my_project.data_base.query.ast.metadata.select.column_method.min.QueryColumnMethodMinAstMetadata

class QueryColumnMethodMinAst(
    override var metadata: QueryColumnMethodMinAstMetadata = QueryColumnMethodMinAstMetadata()
) : IQueryColumnMethodMinAst {

    override var Column:   IQueryColumnsBaseAst?   = null;
    override var Over:     IQueryColumnOverAst? = null;
}