package gog.my_project.data_base_query.dialect.nodes.parameter_nodes

import gog.my_project.data_base_query.dialect.data_class.parameter_data.QueryRenderParameterData
import gog.my_project.data_base_query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base_query.renderer.interfaces.IAstRenderer

interface IQueryRenderParameterCapability : IAstRenderer<IQueryParameterAst<*>, QueryRenderParameterData> {

}