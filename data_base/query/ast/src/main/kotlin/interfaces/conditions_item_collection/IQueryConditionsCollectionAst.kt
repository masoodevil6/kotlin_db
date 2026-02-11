package gog.my_project.data_base.query.ast.interfaces.condition_item_collection

import gog.my_project.data_base.query.ast.interfaces.IQueryAst

interface IQueryConditionsCollectionAst : IQueryAst {

    var paramsCollection: MutableList<Any>;

}