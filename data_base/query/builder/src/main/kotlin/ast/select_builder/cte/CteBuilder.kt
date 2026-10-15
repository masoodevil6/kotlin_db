package gog.my_project.data_base.query.builder.ast.select_builder.cte

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.meta_data.queries.QueryMetadataResolver
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1

/** Builds a query whose explicitly aliased SELECT items are consumable CTE outputs. */
fun cte(
    name: String,
    block: IQueryRenderSelectApi.() -> Unit,
): Cte = buildCteDefinition(name, null, block)

/** Builds a typed runtime Cte while retaining its concrete declaration owner. */
fun cte(
    name: String,
    declarationOwner: KClass<*>,
    block: IQueryRenderSelectApi.() -> Unit,
): Cte = buildCteDefinition(name, declarationOwner, block)

/** Builds a typed CTE from its declaration receiver without repeating its owner type. */
fun ICteDeclaration<*>.buildCte(
    block: IQueryRenderSelectApi.() -> Unit,
): Cte = cte(
    name = QueryMetadataResolver.queryName(this::class),
    declarationOwner = this::class,
    block = block,
)

private fun buildCteDefinition(
    name: String,
    declarationOwner: KClass<*>?,
    block: IQueryRenderSelectApi.() -> Unit,
): Cte {
    require(name.isNotBlank()) { "Cte name must not be blank" }
    val logicalQueryName = declarationOwner?.let { QueryMetadataResolver.queryName(it) }

    val builder = QueryRenderSelectBuilder(
        params = mutableListOf(),
        ast = gog.my_project.data_base.query.ast.schema.select_schema.query_render_select.QueryRenderSelectAst(),
    ).apply {
        requireCteOutputAliases(name, declarationOwner)
        block()
    }

    require(builder.ast.select != null) { "Cte $name must define a SELECT clause" }
    val aliases = builder.ast.selectAliases()
    require(aliases.size == builder.ast.select?.columns?.size) {
        "Every SELECT item in Cte $name must have a non-empty SQL alias"
    }
    require(aliases.isNotEmpty()) { "Cte $name must select at least one aliased output" }
    require(aliases.distinct().size == aliases.size) {
        "Cte $name cannot publish duplicate SQL aliases"
    }
    declarationOwner?.let { validateUniqueLogicalOutputNames(name, it, builder.ast) }

    return BuiltCteComposition(
        name = name,
        definition = builder,
        dependencies = builder.cteDependencies(),
        declarationOwner = declarationOwner,
        logicalQueryName = logicalQueryName,
    )
}

private fun validateUniqueLogicalOutputNames(
    queryName: String,
    declarationOwner: KClass<*>,
    ast: IQueryRenderSelectAst,
) {
    val logicalNames = ast.select?.columns.orEmpty().mapNotNull { output ->
        val property = output.PropertyReference ?: return@mapNotNull null
        val receiverType = property.parameters.singleOrNull()
            ?.takeIf { it.kind == KParameter.Kind.INSTANCE }
            ?.type?.classifier as? KClass<*>
        if (receiverType != declarationOwner) return@mapNotNull null
        QueryMetadataResolver.queryLogicalOutputName(property as KProperty1<*, *>)
    }
    require(logicalNames.distinct().size == logicalNames.size) {
        "Cte $queryName cannot declare duplicate logical output names"
    }
}

/** Deliberately not a data class: CTE composition identity is reference identity. */
internal class BuiltCteComposition(
    override val name: String,
    override val definition: IQueryApi<IQueryRenderSelectAst>,
    override val dependencies: List<Cte>,
    val declarationOwner: KClass<*>?,
    val logicalQueryName: String?,
) : Cte
