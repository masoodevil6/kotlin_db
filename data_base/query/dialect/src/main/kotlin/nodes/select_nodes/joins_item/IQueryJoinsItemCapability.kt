package gog.my_project.data_base.query.dialect.nodes.select_nodes.joins_item

import gog.my_project.data_base.query.ast.interfaces.select_interface.joins_item.IQueryJoinsItemAst
import gog.my_project.data_base.query.dialect.data_class.select_data.joins_item.QueryJoinsItemData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryJoinsItemCapability : IAstRenderer<IQueryJoinsItemAst, QueryJoinsItemData> {
}