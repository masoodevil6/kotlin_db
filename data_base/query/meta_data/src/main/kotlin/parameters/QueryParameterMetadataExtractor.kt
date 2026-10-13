package gog.my_project.data_base.query.meta_data.parameters

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.delete_interface.query_render_delete.IQueryRenderDeleteAst
import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import gog.my_project.data_base.query.ast.interfaces.insert_interface.query_render_insert.IQueryRenderInsertAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.conditions.IQueryIsConditionAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins.IQueryJoinsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins_item.IQueryJoinsItemAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_group.IQueryOptionGroupAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_limit.IQueryOptionLimitAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_order.IQueryOptionOrderAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.select.IQuerySelectAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.where.IQueryWhereAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs.IQueryWithsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs_item.IQueryWithsItemAst
import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst
import gog.my_project.data_base.query.ast.interfaces.update_interface.query_render_update.IQueryRenderUpdateAst
import java.util.IdentityHashMap

/** Extracts parameter definitions and representation from the known query AST contracts. */
object QueryParameterMetadataExtractor {
    fun inspect(ast: IQueryAst): QueryParameterMetadata = Inspection().run {
        visit(ast)
        result()
    }

    private class Inspection {
        private val visitedNodes = IdentityHashMap<IQueryAst, Boolean>()
        private val definitions = IdentityHashMap<SqlParameter<*>, Boolean>()
        private var hasLegacyParameterSource = false
        private var hasUnsupportedParameterSlot = false

        fun visit(node: IQueryAst) {
            if (visitedNodes.put(node, true) != null) return

            when (node) {
                is IQueryRenderSelectAst -> {
                    node.withs?.let(::visit)
                    node.select?.let(::visit)
                    node.table?.let(::visit)
                    node.joins?.let(::visit)
                    node.where?.let(::visit)
                    node.optionGroup?.let(::visit)
                    node.optionOrder?.let(::visit)
                    node.optionLimit?.let(::visit)
                    node.optionOffset?.let(::visit)
                }

                is IQueryRenderInsertAst -> {
                    node.table?.let(::visit)
                    node.columns.forEach(::visit)
                }

                is IQueryRenderUpdateAst -> {
                    node.table?.let(::visit)
                    node.columns.forEach(::visit)
                    node.where?.let(::visit)
                }

                is IQueryRenderDeleteAst -> {
                    node.table?.let(::visit)
                    node.where?.let(::visit)
                }

                is IQueryWithsAst -> node.withs.forEach(::visit)
                is IQueryWithsItemAst -> node.withBody?.let(::visit)
                is IQueryTableAst -> node.derivedQuery?.let(::visit)
                is IQueryJoinsAst -> node.joins.forEach(::visit)
                is IQueryJoinsItemAst -> {
                    visit(node.joinTable)
                    visitCondition(node.joinConditions)
                }

                is IQueryWhereAst -> node.condition?.let(::visitCondition)
                is IQueryConditionsGroupsAst -> node.conditions.forEach(::visitCondition)
                is IQueryConditionsAst -> {
                    node.conditionSideLeft?.let(::visit)
                    inspectConditionRight(node.conditionSideRight)
                }

                is IQueryConditionsCollectionAst -> node.paramsCollection.forEach { value ->
                    when (value) {
                        is SqlParameter<*> -> definitions[value] = true
                        else -> hasLegacyParameterSource = true
                    }
                }

                is IQuerySelectAst -> node.columns.forEach(::visit)
                is IQueryColumnsAst -> node.Column?.let(::visit)
                is IQueryColumnsBaseAst,
                is IQueryOptionLimitAst,
                is IQueryOptionOffsetAst -> Unit

                is IQueryOptionGroupAst -> node.groupByList.forEach(::visit)
                is IQueryOptionOrderAst -> node.orderByList.forEach(::visit)

                is IQueryColumnInsertAst -> {
                    val parameter = node.parameter
                    if (parameter != null) {
                        definitions[parameter] = true
                    } else if (node.columnTag != null) {
                        hasLegacyParameterSource = true
                    }
                }

                is IQueryColumnUpdateAst -> {
                    val parameter = node.parameter
                    if (parameter != null) {
                        definitions[parameter] = true
                    } else if (node.columnTag != null) {
                        hasLegacyParameterSource = true
                    }
                }

                else -> throw UnsupportedOperationException(
                    "Unsupported query AST node: ${node.javaClass.name}"
                )
            }
        }

        private fun visitCondition(condition: IQueryIsConditionAst) {
            when (condition) {
                is IQueryConditionsGroupsAst -> visit(condition)
                is IQueryConditionsAst -> visit(condition)
                else -> throw UnsupportedOperationException(
                    "Unsupported query condition node: ${condition.javaClass.name}"
                )
            }
        }

        private fun inspectConditionRight(value: Any?) {
            when (value) {
                null -> Unit
                is SqlParameter<*> -> definitions[value] = true
                is IQueryColumnsBaseAst -> visit(value)
                is IQueryConditionsCollectionAst -> visit(value)
                is String -> hasLegacyParameterSource = true
                is IQueryAst -> throw UnsupportedOperationException(
                    "Unsupported AST node in conditionSideRight: ${value.javaClass.name}"
                )
                else -> hasUnsupportedParameterSlot = true
            }
        }

        fun result(): QueryParameterMetadata {
            val hasIdentityDefinitions = definitions.isNotEmpty()
            val representation = when {
                hasUnsupportedParameterSlot -> QueryParameterRepresentation.MixedOrUnsupported
                hasIdentityDefinitions && hasLegacyParameterSource ->
                    QueryParameterRepresentation.MixedOrUnsupported
                hasIdentityDefinitions -> QueryParameterRepresentation.IdentityAware
                else -> QueryParameterRepresentation.LegacyOnly
            }

            return QueryParameterMetadata(
                representation = representation,
                definitions = definitions.keys,
            )
        }
    }
}
