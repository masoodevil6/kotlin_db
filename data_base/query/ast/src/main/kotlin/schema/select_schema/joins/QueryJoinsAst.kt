package gog.my_project.data_base.query.ast.schema.select_schema.joins
import gog.my_project.data_base.query.ast.metadata.select.joins.QueryJoinsAstMetadata

import gog.my_project.data_base.query.ast.interfaces.select_interface.joins.IQueryJoinsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins_item.IQueryJoinsItemAst

class QueryJoinsAst(
    override var metadata: QueryJoinsAstMetadata = QueryJoinsAstMetadata()
) : IQueryJoinsAst {

    override var joins: MutableList<IQueryJoinsItemAst> = mutableListOf();

}