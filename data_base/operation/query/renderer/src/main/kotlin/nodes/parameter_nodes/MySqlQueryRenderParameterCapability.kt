package gog.my_project.data_base_query.renderer.nodes.delete_nodes.query

import gog.my_project.data_base_query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base_query.dialect.data_class.parameter_data.QueryRenderParameterData
import gog.my_project.data_base_query.dialect.nodes.parameter_nodes.IQueryRenderParameterCapability
import gog.my_project.data_base_query.renderer.interfaces.IRenderContext
import gog.my_project.data_base_query.renderer.interfaces.IParameterOccurrenceAwareRenderer

class MySqlQueryRenderParameterCapability : IQueryRenderParameterCapability, IParameterOccurrenceAwareRenderer {


    override fun render(
        ast: IQueryParameterAst<*>,
        ctx: IRenderContext,
        dataClass: QueryRenderParameterData?
    ): String {
        return ctx.renderParameter(ast)
    }
}
