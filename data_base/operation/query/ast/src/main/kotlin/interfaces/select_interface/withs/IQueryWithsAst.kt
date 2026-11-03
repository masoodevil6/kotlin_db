package gog.my_project.data_base_query.ast.interfaces.select_interface.withs
import gog.my_project.data_base_query.ast.metadata.select.withs.QueryWithsAstMetadata

import gog.my_project.data_base_query.ast.interfaces.IQueryAst
import gog.my_project.data_base_query.ast.interfaces.select_interface.withs_item.IQueryWithsItemAst

interface IQueryWithsAst : IQueryAst<QueryWithsAstMetadata> {

    var withs: MutableList<IQueryWithsItemAst>;

}