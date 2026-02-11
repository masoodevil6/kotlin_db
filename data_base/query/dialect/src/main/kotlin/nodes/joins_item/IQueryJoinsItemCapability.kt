package gog.my_project.data_base.query.dialect.nodes.joins_item

import gog.my_project.data_base.query.ast.interfaces.joins_item.IQueryJoinsItemAst
import gog.my_project.data_base.query.dialect.data_class.joins_item.QueryJoinsItemData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryJoinsItemCapability : IAstRenderer<IQueryJoinsItemAst , QueryJoinsItemData> {
}