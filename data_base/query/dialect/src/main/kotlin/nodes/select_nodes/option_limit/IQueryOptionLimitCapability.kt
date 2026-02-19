package gog.my_project.data_base.query.dialect.nodes.select_nodes.option_limit

import gog.my_project.data_base.query.ast.interfaces.select_interface.option_limit.IQueryOptionLimitAst
import gog.my_project.data_base.query.dialect.data_class.select_data.option_limit.QueryOptionLimitData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryOptionLimitCapability  : IAstRenderer<IQueryOptionLimitAst, QueryOptionLimitData> {

}