package gog.my_project.data_base.query.renderer.nodes.select_nodes.column_method.max

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.max.IQueryColumnMethodMaxAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.max.QueryColumnMethodMaxData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.column_method.max.IQueryColumnMethodMaxCapability
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext


class MySqlQueryColumnMethodMaxCapability :
    IQueryColumnMethodMaxCapability, IParameterOccurrenceAwareRenderer  {

    override fun render(
        ast: IQueryColumnMethodMaxAst,
        ctx: IRenderContext,
        dataClass: QueryColumnMethodMaxData?
    ): String? {

        return "(${ctx.registry.render(ast.Column , ctx.dialect  , QueryColumnBaseData())  })"

    }


}