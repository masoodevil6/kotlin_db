package gog.my_project.data_base.query.dialect.nodes.withs_item

import gog.my_project.data_base.query.ast.interfaces.withs_item.IQueryWithsItemAst
import gog.my_project.data_base.query.dialect.data_class.withs_item.QueryWithsItemData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryWithsItemCapability : IAstRenderer<IQueryWithsItemAst , QueryWithsItemData> {

}