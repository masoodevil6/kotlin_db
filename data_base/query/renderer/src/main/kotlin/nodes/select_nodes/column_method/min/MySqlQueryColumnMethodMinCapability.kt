package gog.my_project.data_base.query.renderer.nodes.select_nodes.column_method.min

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.min.IQueryColumnMethodMinAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.min.QueryColumnMethodMinData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_over.QueryColumnOverData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.column_method.min.IQueryColumnMethodMinCapability
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryColumnMethodMinCapability :
    IQueryColumnMethodMinCapability, IParameterOccurrenceAwareRenderer  {

    override fun render(
        ast: IQueryColumnMethodMinAst,
        ctx: IRenderContext,
        dataClass: QueryColumnMethodMinData?
    ): String? {

        var rendered = "(${ctx.registry.render(ast.Column, ctx.dialect, QueryColumnBaseData())})"
        ast.Over?.let { over ->
            rendered += " ${ctx.registry.render(over, ctx.dialect, QueryColumnOverData())?.trim()}"
        }
        return rendered

    }

}
