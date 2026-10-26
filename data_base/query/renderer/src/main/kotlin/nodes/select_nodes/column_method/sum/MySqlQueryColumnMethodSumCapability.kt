package gog.my_project.data_base.query.renderer.nodes.select_nodes.column_method.sum

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.sum.IQueryColumnMethodSumAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.sum.QueryColumnMethodSumData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.column_method.sum.IQueryColumnMethodSumCapability
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryColumnMethodSumCapability :
    IQueryColumnMethodSumCapability, IParameterOccurrenceAwareRenderer {
    override fun render(
        ast: IQueryColumnMethodSumAst,
        ctx: IRenderContext,
        dataClass: QueryColumnMethodSumData?,
    ): String?{

        return "(${ctx.registry.render(ast.Column, ctx.dialect, QueryColumnBaseData())  })";

    }
}
