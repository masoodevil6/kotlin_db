package gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection
import gog.my_project.data_base.query.ast.metadata.select.conditions_item_collection.QueryConditionsCollectionAstMetadata

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.conditions.IQueryIsConditionAst
import gog.my_project.data_base.query.ast.metadata.select.conditions_item.QueryConditionsItemAstMetadata

interface IQueryConditionsCollectionAst : IQueryIsConditionAst<QueryConditionsCollectionAstMetadata> {

    /**
     * Ordered collection entries. New DSL entries are IQueryParameterAst<*> definitions; SqlParameter
     * values remain here only for the legacy collection API during migration.
     */
    var paramsCollection: MutableList<Any>

}
