package gog.my_project.data_base.query.renderer.nodes.select_nodes.option_partition

import gog.my_project.data_base.query.ast.interfaces.select_interface.option_partition.IQueryOptionPartitionAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_partition.IQueryOptionPartitionAstPart
import gog.my_project.data_base.query.dialect.data_class.select_data.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.dialect.data_class.select_data.option_partition.QueryOptionPartitionData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.option_partition.IQueryOptionPartitionCapability
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryOptionPartitionCapability:
    IQueryOptionPartitionCapability, IParameterOccurrenceAwareRenderer  {

    override fun render(
        ast: IQueryOptionPartitionAst,
        ctx: IRenderContext,
        dataClass: QueryOptionPartitionData?
    ): String? {

        val renderedParts = ast.parts.map { part ->
            when (part) {
                is IQueryOptionPartitionAstPart.Column -> {
                    ctx.registry.render(
                        part.ast,
                        ctx.dialect,
                        QueryColumnBaseData()
                    )
                }
            }
        }

        return " ${ctx.dialect._prefixPartitionBy} ${renderedParts.joinToString(", ")} "

    }

}