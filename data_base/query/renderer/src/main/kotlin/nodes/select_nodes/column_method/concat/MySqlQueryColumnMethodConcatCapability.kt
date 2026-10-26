package gog.my_project.data_base.query.renderer.nodes.select_nodes.column_method.concat

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.concat.IQueryColumnMethodConcatAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.concat.IQueryColumnMethodConcatAstPart
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.concat.QueryColumnMethodConcatData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.column_method.concat.IQueryColumnMethodConcatCapability
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryColumnMethodConcatCapability:
    IQueryColumnMethodConcatCapability, IParameterOccurrenceAwareRenderer {

    override fun render(
        ast: IQueryColumnMethodConcatAst,
        ctx: IRenderContext,
        dataClass: QueryColumnMethodConcatData?
    ): String? {

        val renderedParts = ast.parts.map { part ->
            when (part) {
                is IQueryColumnMethodConcatAstPart.Column -> {
                    ctx.registry.render(
                        part.ast,
                        ctx.dialect,
                        QueryColumnBaseData()
                    )
                }

                is IQueryColumnMethodConcatAstPart.Text -> {
                    "'${part.value.replace("'", "''")}'"
                }
            }
        }

        return "(${renderedParts.joinToString(", ")})"
    }

}
