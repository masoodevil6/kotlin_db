package gog.my_project.data_base.query.dialect.nodes.option_limit

import gog.my_project.data_base.query.ast.interfaces.option_limit.IQueryOptionLimitAst
import gog.my_project.data_base.query.dialect.data_class.option_limit.QueryOptionLimitData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryOptionLimitCapability  : IAstRenderer<IQueryOptionLimitAst , QueryOptionLimitData> {

}