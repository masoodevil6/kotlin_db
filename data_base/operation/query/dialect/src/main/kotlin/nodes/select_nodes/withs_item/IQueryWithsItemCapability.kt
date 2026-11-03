package gog.my_project.data_base_query.dialect.nodes.select_nodes.withs_item

import gog.my_project.data_base_query.ast.interfaces.select_interface.withs_item.IQueryWithsItemAst
import gog.my_project.data_base_query.dialect.data_class.select_data.withs_item.QueryWithsItemData
import gog.my_project.data_base_query.renderer.interfaces.IAstRenderer

interface IQueryWithsItemCapability : IAstRenderer<IQueryWithsItemAst, QueryWithsItemData> {

}