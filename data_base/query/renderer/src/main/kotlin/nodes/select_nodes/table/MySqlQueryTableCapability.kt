package gog.my_project.data_base.query.renderer.nodes.select_nodes.table

import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.dialect.data_class.select_data.table.QueryTableData
import gog.my_project.data_base.query.dialect.data_class.select_data.query_render_select.IQueryRenderSelectData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.table.IQueryTableCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer

class MySqlQueryTableCapability :
    IQueryTableCapability, IParameterOccurrenceAwareRenderer {

    override fun render(
        ast:         IQueryTableAst,
        ctx:         IRenderContext,
        dataClass:   QueryTableData?
    ): String? {

        val sourceName = ast.metadata.resolveName()
        val sourceAlias = ast.metadata.resolveAlias()

        ast.derivedQuery?.let { nestedQuery ->
            require(sourceName == null) {
                "A table source cannot combine a derived SELECT with a table or CTE"
            }
            val nestedSql = ctx.registry.render(nestedQuery, ctx.dialect, IQueryRenderSelectData())
                ?: error("Could not render nested SELECT source")
            require(!sourceAlias.isNullOrBlank()) {
                "A nested SELECT source requires a non-empty alias"
            }
            val prefix = if (dataClass?._withPrefix == true) "${ctx.dialect._prefixTable} " else ""
            return " $prefix ( $nestedSql ) As $sourceAlias "
        }

        var queryTemp: String = "";
        if (sourceName != null) {
            if (dataClass?._withPrefix == true){
                queryTemp = " ${ctx.dialect._prefixTable} ";
            }
            queryTemp += " ${sourceName} ";
            if (!sourceAlias.isNullOrEmpty() && dataClass?._withAlias == true) {
                queryTemp += " As ${sourceAlias} ";
            }
        }

        return queryTemp;
    }

}
