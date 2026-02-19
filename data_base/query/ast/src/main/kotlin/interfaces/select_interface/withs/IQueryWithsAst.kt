package gog.my_project.data_base.query.ast.interfaces.select_interface.withs

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs_item.IQueryWithsItemAst

interface IQueryWithsAst : IQueryAst {

    var withs: MutableList<IQueryWithsItemAst>;

}