package gog.my_project.data_base.query.ast.schema.select_schema.conditions_item_collection

import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst

class QueryConditionsCollectionAst :
    IQueryConditionsCollectionAst {

    override var paramsCollection: MutableList<Any>  = mutableListOf();

}