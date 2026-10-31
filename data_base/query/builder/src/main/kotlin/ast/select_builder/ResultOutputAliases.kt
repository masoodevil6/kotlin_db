package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst

/** Validates the SELECT output names required by key-based ResultSet materialization. */
fun IQueryRenderSelectApi.requireResultOutputAliases(): List<String> {
    val columns = ast.select?.columns
        ?: throw IllegalArgumentException("A SELECT clause is required for result consumption")
    require(columns.isNotEmpty()) { "At least one SELECT output is required for result consumption" }

    val aliases = columns.mapIndexed { index, column ->
        column.metadata.ColumnAlias?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("SELECT output at index $index must have a non-empty SQL alias")
    }
    require(aliases.distinct().size == aliases.size) {
        "SELECT output aliases must be unique for result consumption"
    }
    return aliases
}

internal fun IQueryRenderSelectAst.selectAliases(): List<String> =
    select?.columns.orEmpty()
        .mapNotNull {
            it.metadata.ColumnAlias?.takeIf { name -> name.isNotBlank() }
        }
