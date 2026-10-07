package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import kotlin.reflect.KClass

/** Keeps typed SELECT qualifiers aligned with the effective table alias for one query. */
internal class ModelTableAliasContext {

    private val effectiveAliases = mutableMapOf<KClass<*>, String>()
    private val typedColumns = mutableMapOf<KClass<*>, MutableList<IQueryColumnsBaseAst>>()

    fun registerTable(model: KClass<*>, effectiveAlias: String) {
        effectiveAliases[model] = effectiveAlias
        typedColumns[model]?.forEach { it.tableAlias = effectiveAlias }
    }

    fun registerColumn(model: KClass<*>, column: IQueryColumnsBaseAst) {
        typedColumns.getOrPut(model) { mutableListOf() }.add(column)
        effectiveAliases[model]?.let { column.tableAlias = it }
    }
}
