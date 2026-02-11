package gog.my_project.data_base.query.builder.interfaces.conditions_item_collection

import gog.my_project.data_base.query.ast.interfaces.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.builder.interfaces.IQuery

interface IQueryConditionsCollectionBuilder : IQuery {

    var ast: IQueryConditionsCollectionAst;

    fun <T> addParam(paramValue: T) : IQueryConditionsCollectionBuilder;

}