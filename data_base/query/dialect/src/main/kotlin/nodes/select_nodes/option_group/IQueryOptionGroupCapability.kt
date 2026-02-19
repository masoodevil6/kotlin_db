package gog.my_project.data_base.query.dialect.nodes.select_nodes.option_group

import gog.my_project.data_base.query.ast.interfaces.select_interface.option_group.IQueryOptionGroupAst
import gog.my_project.data_base.query.dialect.data_class.select_data.option_group.QueryOptionGroupData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryOptionGroupCapability: IAstRenderer<IQueryOptionGroupAst, QueryOptionGroupData> {
}