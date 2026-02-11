package gog.my_project.data_base.query.dialect.nodes.option_offset

import gog.my_project.data_base.query.ast.interfaces.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.dialect.data_class.option_offset.QueryOptionOffsetData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryOptionOffsetCapability: IAstRenderer<IQueryOptionOffsetAst , QueryOptionOffsetData> {
}