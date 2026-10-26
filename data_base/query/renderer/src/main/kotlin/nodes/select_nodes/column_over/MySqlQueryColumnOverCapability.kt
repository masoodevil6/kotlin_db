package gog.my_project.data_base.query.renderer.nodes.select_nodes.column_over

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_over.IQueryColumnOverAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column_over.QueryColumnOverData
import gog.my_project.data_base.query.dialect.data_class.select_data.option_order.QueryOptionOrderData
import gog.my_project.data_base.query.dialect.data_class.select_data.option_partition.QueryOptionPartitionData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.column_over.IQueryColumnOverCapability
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryColumnOverCapability:
    IQueryColumnOverCapability, IParameterOccurrenceAwareRenderer  {

    override fun render(
        ast: IQueryColumnOverAst,
        ctx: IRenderContext,
        dataClass: QueryColumnOverData?
    ): String? {

        if (ast.partitionBy == null && ast.orderBy == null) {
            return " ${ctx.dialect._prefixOver}() "
        }

        var argsOts = " ${ctx.dialect._prefixOver}( ";
        if (ast.partitionBy != null){
            argsOts += " ${ctx.registry.render(ast.partitionBy, ctx.dialect, QueryOptionPartitionData())} "
        }
        if (ast.orderBy != null){
            argsOts += " ${ctx.registry.render(ast.orderBy, ctx.dialect, QueryOptionOrderData())} "
        }
        argsOts += " ) "

        return argsOts
    }

}
