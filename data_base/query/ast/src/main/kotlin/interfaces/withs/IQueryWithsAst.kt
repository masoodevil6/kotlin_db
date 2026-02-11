package gog.my_project.data_base.query.ast.interfaces.withs

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.withs_item.IQueryWithsItemAst

interface IQueryWithsAst : IQueryAst {

    var withs: MutableList<IQueryWithsItemAst>;

}