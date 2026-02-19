package gog.my_project.data_base.query.dialect.nodes.select_nodes.option_offset

import gog.my_project.data_base.query.ast.interfaces.select_interface.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.dialect.data_class.select_data.option_offset.QueryOptionOffsetData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryOptionOffsetCapability: IAstRenderer<IQueryOptionOffsetAst, QueryOptionOffsetData> {
}