package gog.my_project.data_base.query.renderer.nodes.delete_nodes.query

import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.query.dialect.data_class.parameter_data.QueryRenderParameterData
import gog.my_project.data_base.query.dialect.nodes.parameter_nodes.IQueryRenderParameterCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer
import gog.my_project.data_base.query.renderer.interfaces.renderParameter

class MySqlQueryRenderParameterCapability : IQueryRenderParameterCapability, IParameterOccurrenceAwareRenderer {


    override fun render(
        ast: IQueryParameterAst<*>,
        ctx: IRenderContext,
        dataClass: QueryRenderParameterData?
    ): String {
        return ctx.renderParameter(ast)
    }
}
