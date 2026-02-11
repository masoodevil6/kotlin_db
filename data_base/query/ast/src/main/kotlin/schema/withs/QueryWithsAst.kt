package gog.my_project.data_base.query.ast.schema.withs

import gog.my_project.data_base.query.ast.interfaces.withs.IQueryWithsAst
import gog.my_project.data_base.query.ast.interfaces.withs_item.IQueryWithsItemAst

class QueryWithsAst : IQueryWithsAst {

    override var withs: MutableList<IQueryWithsItemAst> = mutableListOf();

}