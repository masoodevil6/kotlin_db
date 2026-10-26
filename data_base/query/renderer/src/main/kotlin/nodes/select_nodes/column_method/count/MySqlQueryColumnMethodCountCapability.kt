package gog.my_project.data_base.query.renderer.nodes.select_nodes.column_method.count

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.count.IQueryColumnMethodCountAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.count.IQueryColumnMethodCountData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.column_method.count.IQueryColumnMethodCountCapability
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryColumnMethodCountCapability :
    IQueryColumnMethodCountCapability, IParameterOccurrenceAwareRenderer {

    override fun render(
        ast: IQueryColumnMethodCountAst,
        ctx: IRenderContext,
        dataClass: IQueryColumnMethodCountData?
    ): String? {

        val isDistinct = ast.metadata.isDistinct;
        val renderedParts = ast.columns.map { itemColumnAst ->
            ctx.registry.render(
                itemColumnAst,
                ctx.dialect,
                QueryColumnBaseData()
            )
        }

        return "(${if (isDistinct) ctx.dialect._prefixDistinct else ""} ${renderedParts.joinToString(", ")}) "

    }

}
