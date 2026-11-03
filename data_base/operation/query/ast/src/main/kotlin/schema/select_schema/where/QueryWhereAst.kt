package gog.my_project.data_base_query.ast.schema.select_schema_ast.where
import gog.my_project.data_base_query.ast.metadata.select.where.QueryWhereAstMetadata

import gog.my_project.data_base_query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base_query.ast.interfaces.select_interface.where.IQueryWhereAst

class QueryWhereAst(
    override var metadata: QueryWhereAstMetadata = QueryWhereAstMetadata()
) : IQueryWhereAst {

    override var condition: IQueryConditionsGroupsAst?  = null;

}