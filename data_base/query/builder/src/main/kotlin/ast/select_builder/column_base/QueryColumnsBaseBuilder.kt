package gog.my_project.data_base.query.builder.ast.select_builder.column_base

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteSourceContext
import gog.my_project.data_base.query.builder.ast.select_builder.cte.BuiltCteComposition
import gog.my_project.data_base.query.builder.metadata.models.ModelMetadataResolver
import gog.my_project.data_base.query.builder.metadata.queries.QueryMetadataResolver
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1

class QueryColumnsBaseBuilder(
    override var ast: IQueryColumnsBaseAst = QueryColumnsBaseAst(),
) : IQueryColumnsBaseApi {

    private var modelAliasContext = ModelTableAliasContext()
    private var querySourceContext = CteSourceContext()

    private sealed interface SelectedSource {
        data class Sql(val name: String) : SelectedSource
        data class Model(val type: KClass<*>) : SelectedSource
        data class Query(val type: KClass<*>) : SelectedSource
        class Relation(val value: Cte) : SelectedSource
    }

    private var selectedSource: SelectedSource? = null

    internal var modelProperty: KProperty1<*, *>? = null
        private set
    internal var cteProperty: KProperty1<*, *>? = null
        private set

    internal constructor(
        ast: IQueryColumnsBaseAst,
        modelAliasContext: ModelTableAliasContext,
        querySourceContext: CteSourceContext,
    ) : this(ast) {
        this.modelAliasContext = modelAliasContext
        this.querySourceContext = querySourceContext
    }

    override fun tableSelector(sourceName: String): IQueryColumnsBaseApi {
        val nextSource = SelectedSource.Sql(sourceName)
        selectSource(nextSource)
        modelAliasContext.registerSqlSourceColumn(sourceName, ast)
        refreshTypedColumnForSelectedSource()
        return this
    }

    override fun tableSelector(source: KClass<*>): IQueryColumnsBaseApi {
        val nextSource: SelectedSource
        val effectiveAlias: String
        if (IModelBase::class.java.isAssignableFrom(source.java)) {
            ModelMetadataResolver.requireTable(source)
            nextSource = SelectedSource.Model(source)
            effectiveAlias = modelAliasContext.effectiveAlias(source)
        } else {
            val canonicalName = canonicalQueryName(source)
            effectiveAlias = querySourceContext.validateTypedSource(source, canonicalName)
            nextSource = SelectedSource.Query(source)
        }

        selectSource(nextSource)
        when (nextSource) {
            is SelectedSource.Model -> {
                modelAliasContext.applyModelAlias(nextSource.type, ast.metadata)
                check(ast.metadata.resolveTableAlias() == effectiveAlias)
                modelAliasContext.registerColumn(nextSource.type, ast)
            }
            else -> {
                querySourceContext.applyOccurrenceAlias(ast.metadata)
                check(ast.metadata.resolveTableAlias() == effectiveAlias)
            }
        }
        refreshTypedColumnForSelectedSource()
        return this
    }




    override fun columnSelector(columnName: String): IQueryColumnsBaseApi {
        clearColumn()
        if (selectedSource == null) clearTableAlias()
        ast.metadata.columnNameFromAst = columnName
        return this
    }

    @Deprecated("Use tableSelector/columnSelector with a typed Query output when available")
    override fun cteColumn(cte: Cte, sqlOutputName: String): IQueryColumnsBaseApi {
        val (effectiveAlias, publishedSqlName) = querySourceContext.resolve(cte, sqlOutputName)
        selectSource(SelectedSource.Relation(cte))
        clearColumn()
        querySourceContext.applyOccurrenceAlias(ast.metadata)
        check(ast.metadata.resolveTableAlias() == effectiveAlias)
        ast.metadata.columnNameFromAst = publishedSqlName
        return this
    }

    override fun columnSelector(property: KProperty1<*, *>): IQueryColumnsBaseApi {
        val ownerType = typedPropertyOwner(property)
        return columnSelector(ownerType, property)
    }

    override fun columnSelector(
        declaration: KClass<*>,
        property: KProperty1<*, *>,
    ): IQueryColumnsBaseApi {
        val ownerType = typedPropertyOwner(property)
        require(ownerType == declaration ||
            (IModelBase::class.java.isAssignableFrom(declaration.java) && ownerType.java.isAssignableFrom(declaration.java))
        ) {
            "Property ${property.name} belongs to ${ownerType.qualifiedName}, not ${declaration.qualifiedName}"
        }

        clearColumn()
        when {
            IModelBase::class.java.isAssignableFrom(ownerType.java) -> {
                ModelMetadataResolver.requireTable(declaration)
                ModelMetadataResolver.requireColumn(declaration, property)
                requireSelectedModelMatches(declaration)
                if (selectedSource == null) clearTableAlias()
                ast.metadata.columnNameFromModel = ModelMetadataResolver.columnNameFromModel(property)
                ast.metadata.columnNameFromAnnotation =
                    ModelMetadataResolver.columnNameFromAnnotation(declaration, property)
                ast.metadata.columnNameFromAst = null
                ast.metadata.columnAliasFromModel = ModelMetadataResolver.columnAliasFromModel(property)
                ast.metadata.columnAliasFromAnnotation = ModelMetadataResolver.columnAliasFromAnnotation(property)
                modelProperty = property
                if (selectedSource is SelectedSource.Model) {
                    modelAliasContext.registerColumn((selectedSource as SelectedSource.Model).type, ast)
                }
            }
            else -> {
                requireSelectedQueryMatches(ownerType)
                val canonicalName = canonicalQueryName(ownerType)
                val (effectiveAlias, publishedSqlName) = querySourceContext.validateTypedOutput(
                    ownerType,
                    property,
                    canonicalName,
                )
                if (selectedSource == null) clearTableAlias()
                when (selectedSource) {
                    is SelectedSource.Query -> {
                        querySourceContext.applyOccurrenceAlias(ast.metadata)
                        require(ast.metadata.resolveTableAlias() == effectiveAlias)
                    }
                    is SelectedSource.Sql -> require(ast.metadata.resolveTableAlias() == effectiveAlias) {
                        "Selected source ${ast.metadata.resolveTableAlias()} does not match typed Query source alias $effectiveAlias"
                    }
                    is SelectedSource.Relation -> require(
                        querySourceContext.snapshot()?.relation === (selectedSource as SelectedSource.Relation).value &&
                            ast.metadata.resolveTableAlias() == effectiveAlias,
                    ) {
                        "Selected Cte relation does not match the active typed Query source"
                    }
                    null -> Unit
                    is SelectedSource.Model -> error("A model source cannot select a typed Query output")
                }
                ast.metadata.columnNameFromModel = property.name
                ast.metadata.columnNameFromAnnotation = QueryMetadataResolver.queryPublishedAliasFromAnnotation(property)
                ast.metadata.columnNameFromAst = publishedSqlName
                ast.metadata.columnAliasFromModel = QueryMetadataResolver.queryPublishedAliasFromModel(property)
                ast.metadata.columnAliasFromAnnotation = QueryMetadataResolver.queryPublishedAliasFromAnnotation(property)
                cteProperty = property
            }
        }
        return this
    }




    private fun selectSource(nextSource: SelectedSource) {
        val previousSource = selectedSource
        if (previousSource != null && !sameSource(previousSource, nextSource)) {
            detachSource()
            clearColumn()
        } else if (previousSource == null) {
            modelAliasContext.unregisterSqlSourceColumn(ast)
        }
        selectedSource = nextSource
    }

    private fun sameSource(left: SelectedSource, right: SelectedSource): Boolean = when {
        left is SelectedSource.Relation && right is SelectedSource.Relation -> left.value === right.value
        else -> left == right
    }

    private fun detachSource() {
        modelAliasContext.unregisterColumn(ast)
        modelAliasContext.unregisterSqlSourceColumn(ast)
    }

    private fun clearColumn() {
        ast.metadata.columnNameFromModel = null
        ast.metadata.columnNameFromAnnotation = null
        ast.metadata.columnNameFromAst = null
        ast.metadata.columnAliasFromModel = null
        ast.metadata.columnAliasFromAnnotation = null
        modelProperty = null
        cteProperty = null
    }

    private fun clearTableAlias() {
        ast.metadata.tableAliasFromModel = null
        ast.metadata.tableAliasFromAnnotation = null
        ast.metadata.tableAliasFromAst = null
    }

    private fun refreshTypedColumnForSelectedSource() {
        val model = modelProperty
        if (model != null) {
            val owner = typedPropertyOwner(model)
            requireSelectedModelMatches(owner)
        }
        val query = cteProperty
        if (query != null) {
            val owner = typedPropertyOwner(query)
            requireSelectedQueryMatches(owner)
            val (effectiveAlias, publishedSqlName) = querySourceContext.validateTypedOutput(
                owner,
                query,
                canonicalQueryName(owner),
            )
            when (selectedSource) {
                is SelectedSource.Query -> {
                    querySourceContext.applyOccurrenceAlias(ast.metadata)
                    require(ast.metadata.resolveTableAlias() == effectiveAlias)
                }
                is SelectedSource.Sql -> require(ast.metadata.resolveTableAlias() == effectiveAlias) {
                    "Selected source ${ast.metadata.resolveTableAlias()} does not match typed Query source alias $effectiveAlias"
                }
                is SelectedSource.Relation -> require(
                    querySourceContext.snapshot()?.relation === (selectedSource as SelectedSource.Relation).value &&
                        ast.metadata.resolveTableAlias() == effectiveAlias,
                ) {
                    "Selected Cte relation does not match the active typed Query source"
                }
                else -> Unit
            }
            ast.metadata.columnNameFromModel = query.name
            ast.metadata.columnNameFromAnnotation = QueryMetadataResolver.queryPublishedAliasFromAnnotation(query)
            ast.metadata.columnNameFromAst = publishedSqlName
            ast.metadata.columnAliasFromModel = QueryMetadataResolver.queryPublishedAliasFromModel(query)
            ast.metadata.columnAliasFromAnnotation = QueryMetadataResolver.queryPublishedAliasFromAnnotation(query)
        }
    }

    private fun requireSelectedModelMatches(propertyOwner: KClass<*>) {
        val source = selectedSource
        if (source is SelectedSource.Model) {
            require(propertyOwner.java.isAssignableFrom(source.type.java)) {
                "Model property owner ${propertyOwner.qualifiedName} does not match selected source ${source.type.qualifiedName}"
            }
        } else if (source is SelectedSource.Query || source is SelectedSource.Relation) {
            throw IllegalArgumentException("A Query source cannot select a Model property")
        }
    }

    private fun requireSelectedQueryMatches(propertyOwner: KClass<*>) {
        when (val source = selectedSource) {
            is SelectedSource.Query -> require(source.type == propertyOwner) {
                "Typed output owner ${propertyOwner.qualifiedName} does not match selected source ${source.type.qualifiedName}"
            }
            is SelectedSource.Model -> throw IllegalArgumentException("A Model source cannot select a typed Query output")
            else -> Unit
        }
    }

    private fun canonicalQueryName(ownerType: KClass<*>): String? =
        if (ICteDeclaration::class.java.isAssignableFrom(ownerType.java)) null
        else QueryMetadataResolver.queryName(ownerType)

    private fun <T, R> typedPropertyOwner(property: KProperty1<T, R>): KClass<*> {
        val instanceParameter = property.parameters.singleOrNull()
            ?.takeIf { it.kind == KParameter.Kind.INSTANCE }
            ?: throw IllegalArgumentException("Typed column must be a member property reference")
        return instanceParameter.type.classifier as? KClass<*>
            ?: throw IllegalArgumentException("Cannot resolve the owner type of typed column ${property.name}")
    }
}
