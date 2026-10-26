package gog.my_project.data_base.query.ast.schema.select_schema_ast.withs
import gog.my_project.data_base.query.ast.metadata.select.withs.QueryWithsAstMetadata

import gog.my_project.data_base.query.ast.interfaces.select_interface.withs.IQueryWithsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs_item.IQueryWithsItemAst

class QueryWithsAst(
    override var metadata: QueryWithsAstMetadata = QueryWithsAstMetadata()
) : IQueryWithsAst {

    override var withs: MutableList<IQueryWithsItemAst> = mutableListOf()

}