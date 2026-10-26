package gog.my_project.data_base.query.dialect.nodes.parameter_nodes

import gog.my_project.data_base.query.dialect.data_class.parameter_data.QueryRenderParameterData
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryRenderParameterCapability : IAstRenderer<IQueryParameterAst<*>, QueryRenderParameterData> {

}