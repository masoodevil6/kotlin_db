package gog.my_project.data_base.query.renderer.nodes.select_nodes.column_method.avg

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.avg.IQueryColumnMethodAvgAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column.QueryColumnData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.avg.QueryColumnMethodAvgData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_over.QueryColumnOverData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.column_method.avg.IQueryColumnMethodAvgCapability
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryColumnMethodAvgCapability :
    IQueryColumnMethodAvgCapability, IParameterOccurrenceAwareRenderer {
    override fun render(
        ast: IQueryColumnMethodAvgAst,
        ctx: IRenderContext,
        dataClass: QueryColumnMethodAvgData?,
    ): String? {

        var strOut =  "(${ctx.registry.render(ast.Column, ctx.dialect, QueryColumnBaseData())  })";
        if (ast.Over != null){
            strOut +=  " ${ctx.registry.render(ast.Over, ctx.dialect, QueryColumnOverData())  } ";
        }

        return strOut;
    }

}
