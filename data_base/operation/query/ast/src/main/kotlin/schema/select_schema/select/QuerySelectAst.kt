package gog.my_project.data_base_query.ast.schema.select_schema.select
import gog.my_project.data_base_query.ast.metadata.select.select.QuerySelectAstMetadata

import gog.my_project.data_base_query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base_query.ast.interfaces.select_interface.select.IQuerySelectAst


class QuerySelectAst(
    override var metadata: QuerySelectAstMetadata = QuerySelectAstMetadata()
) : IQuerySelectAst {

    override var columns: MutableList<IQueryColumnsAst> = mutableListOf()

}