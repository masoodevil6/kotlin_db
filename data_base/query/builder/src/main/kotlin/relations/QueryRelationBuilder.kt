package gog.my_project.data_base.query.builder.relations

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.relations.QueryDefinition
import gog.my_project.data_base.query.api.interfaces.relations.IQueryRelation
import gog.my_project.data_base.query.api.interfaces.relations.QueryRelation
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.selectAliases
import kotlin.reflect.KClass

/** Builds a query whose explicitly aliased SELECT items are consumable relation outputs. */
fun queryRelation(
    name: String,
    block: IQueryRenderSelectApi.() -> Unit,
): QueryRelation = buildQueryRelation(name, null, block)

/** Builds a typed runtime QueryRelation while retaining its concrete declaration owner. */
fun queryRelation(
    name: String,
    declarationOwner: KClass<*>,
    block: IQueryRenderSelectApi.() -> Unit,
): QueryRelation = buildQueryRelation(name, declarationOwner, block)

/** Builds a typed relation from its declaration receiver without repeating its owner type. */
fun IQueryRelation<*>.buildQueryRelation(
    block: IQueryRenderSelectApi.() -> Unit,
): QueryRelation = queryRelation(
    name = relationName,
    declarationOwner = this::class,
    block = block,
)

private fun buildQueryRelation(
    name: String,
    declarationOwner: KClass<*>?,
    block: IQueryRenderSelectApi.() -> Unit,
): QueryRelation {
    require(name.isNotBlank()) { "QueryRelation name must not be blank" }

    val builder = QueryRenderSelectBuilder(
        params = mutableListOf(),
        ast = gog.my_project.data_base.query.ast.schema.select_schema.query_render_select.QueryRenderSelectAst(),
    ).apply {
        requireRelationOutputAliases(name, declarationOwner)
        block()
    }

    require(builder.ast.select != null) { "QueryRelation $name must define a SELECT clause" }
    val aliases = builder.ast.selectAliases()
    require(aliases.size == builder.ast.select?.columns?.size) {
        "Every SELECT item in QueryRelation $name must have a non-empty SQL alias"
    }
    require(aliases.isNotEmpty()) { "QueryRelation $name must select at least one aliased output" }
    require(aliases.distinct().size == aliases.size) {
        "QueryRelation $name cannot publish duplicate SQL aliases"
    }

    val definition = BuiltQueryDefinition(
        ast = builder.ast,
        params = builder.params.toList(),
    )
    return BuiltQueryRelation(
        name = name,
        definition = definition,
        dependencies = builder.relationDependencies(),
        declarationOwner = declarationOwner,
    )
}

private data class BuiltQueryDefinition(
    override val ast: IQueryRenderSelectAst,
    override val params: List<SqlParameter<*>>,
) : QueryDefinition

/** Deliberately not a data class: QueryRelation identity is reference identity. */
internal class BuiltQueryRelation(
    override val name: String,
    override val definition: QueryDefinition,
    override val dependencies: List<QueryRelation>,
    val declarationOwner: KClass<*>?,
) : QueryRelation
