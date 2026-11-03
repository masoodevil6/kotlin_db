package gog.my_project.data_base_query.ast.schema.select_schema.conditions_item_collection
import gog.my_project.data_base_query.ast.metadata.select.conditions_item_collection.QueryConditionsCollectionAstMetadata

import gog.my_project.data_base_query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst

class QueryConditionsCollectionAst(
    override var metadata: QueryConditionsCollectionAstMetadata = QueryConditionsCollectionAstMetadata()
) : IQueryConditionsCollectionAst {

    override var paramsCollection: MutableList<Any>  = mutableListOf();

}