package gog.my_project.data_base.query.builder.ast.select_builder.column

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.column.IQueryColumnsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.api.tools.enums.SqlMethodColumn
import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.ast.schema.select_schema.column.QueryColumnsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteSourceContext
import gog.my_project.data_base.query.builder.ast.select_builder.column_base.QueryColumnsBaseBuilder
import gog.my_project.data_base.query.meta_data.models.ModelMetadataResolver
import gog.my_project.data_base.query.meta_data.queries.QueryMetadataResolver
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1

class QueryColumnsBuilder internal constructor(
    override var params: MutableList<SqlParameter<*>>,
    override var ast: IQueryColumnsAst,
    private val modelAliasContext: ModelTableAliasContext,
    private val querySourceContext: CteSourceContext,
    private val queryDefinitionName: String?,
    private val queryDeclarationOwner: KClass<*>?,
) : IQueryColumnsApi {

    constructor(
        params: MutableList<SqlParameter<*>> = mutableListOf(),
        ast: IQueryColumnsAst = QueryColumnsAst(),
    ) : this(params, ast, ModelTableAliasContext(), CteSourceContext(), null, null)

    private var explicitAliasWasSet = false
    internal val hasExplicitAlias: Boolean get() = explicitAliasWasSet
    private var explicitExecutionTypeWasSet = ast.ExecutionType != null


    /* ==============================================================
    structure [method]
    ============================================================== */

    override fun method(method: SqlMethodColumn): IQueryColumnsApi {
        this.ast.ColumnMethod = method.value;
        clearPropertyReferenceAndInferredExecutionType()
        return this;
    }

    override fun sum(): IQueryColumnsApi {
        return this.method(SqlMethodColumn.Sum);
    }

    override fun count(): IQueryColumnsApi {
        return this.method(SqlMethodColumn.Count);
    }

    override fun avg(): IQueryColumnsApi {
        return this.method(SqlMethodColumn.Avg);
    }

    override fun min(): IQueryColumnsApi {
        return this.method(SqlMethodColumn.Min);
    }

    override fun max(): IQueryColumnsApi {
        return this.method(SqlMethodColumn.Max);
    }

    override fun execute(dataType: DataType): IQueryColumnsApi {
        ast.ExecutionType = dataType
        ast.IsExecutionTypeExplicit = true
        explicitExecutionTypeWasSet = true
        return this
    }



    /* ==============================================================
    structure [column]
    ============================================================== */

    override fun column(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnsApi {
        var ast = QueryColumnsBaseAst();
        QueryColumnsBaseBuilder(
            params,
            ast
        ).apply(blockColumn);
        this.ast.Column = ast;
        this.ast.PropertyReference = null
        clearInferredExecutionType()
        if (!explicitAliasWasSet) {
            this.ast.ColumnAlias = null
        }
        return this;
    }

    override fun <T : IModelBase, R> column(
        table: KClass<T>,
        property: KProperty1<T, R>,
    ): IQueryColumnsApi {
        ModelMetadataResolver.requireTable(table)
        ModelMetadataResolver.requireColumn(table, property)

        val baseAst = QueryColumnsBaseAst().apply {
            tableAlias = ModelMetadataResolver.tableAlias(table)
            column = ModelMetadataResolver.columnName(table, property)
        }
        modelAliasContext.registerColumn(table, baseAst)
        this.ast.Column = baseAst

        if (ast.ColumnMethod == null) {
            ast.PropertyReference = property
            inferExecutionType(property)
        } else {
            ast.PropertyReference = null
            clearInferredExecutionType()
        }

        if (!explicitAliasWasSet) {
            this.ast.ColumnAlias = ModelMetadataResolver.columnAlias(property)
        }
        return this
    }

    private fun inferExecutionType(property: KProperty1<*, *>) {
        if (explicitExecutionTypeWasSet) return

        val propertyType = property.returnType.classifier as? KClass<*>
        ast.ExecutionType = when (propertyType) {
            Int::class -> DataType.INT
            Long::class -> DataType.LONG
            String::class -> DataType.STRING
            else -> null
        }
    }

    private fun clearInferredExecutionType() {
        if (!explicitExecutionTypeWasSet) {
            ast.ExecutionType = null
        }
    }

    private fun clearPropertyReferenceAndInferredExecutionType() {
        ast.PropertyReference = null
        clearInferredExecutionType()
    }

    override fun cteOutput(
        relation: Cte,
        sqlOutputName: String,
    ): IQueryColumnsApi {
        clearPropertyReferenceAndInferredExecutionType()
        val (alias, sqlName) = querySourceContext.resolve(relation, sqlOutputName)
        ast.Column = QueryColumnsBaseAst().apply {
            cteAlias = alias
            select = sqlName
        }
        if (!explicitAliasWasSet) {
            ast.ColumnAlias = null
        }
        return this
    }

    override fun <T, R> cteOutput(
        property: KProperty1<T, R>,
        qualifier: String?,
    ): IQueryColumnsApi {
        val isPropertyBacked = ast.ColumnMethod == null
        val ownerType = typedPropertyOwner(property)
        val canonicalRelationName = if (ICteDeclaration::class.java.isAssignableFrom(ownerType.java)) {
            null
        } else {
            resolveTypedRelationProperty(property)
        }
        val (resolvedRelationName, sqlOutputName) = querySourceContext.validateTypedOutput(
            ownerType,
            property,
            canonicalRelationName,
        )
        require(qualifier == null || qualifier.isNotBlank()) {
            "Relation source qualifier must not be blank"
        }
        ast.Column = QueryColumnsBaseAst().apply {
            cteAlias = qualifier ?: resolvedRelationName
            select = sqlOutputName
        }
        ast.PropertyReference = property.takeIf { isPropertyBacked }
        if (isPropertyBacked) {
            inferExecutionType(property)
        } else {
            clearInferredExecutionType()
        }
        if (!explicitAliasWasSet) {
            ast.ColumnAlias = QueryMetadataResolver.queryPublishedAlias(property).takeIf { isPropertyBacked }
        }
        return this
    }



    /* ==============================================================
    structure [alias]
    ============================================================== */
    override fun alias(alias: String): IQueryColumnsApi {
        explicitAliasWasSet = true
        this.ast.ColumnAlias = alias.takeIf { it.isNotEmpty() };
        return this;
    }

    override fun <T, R> alias(property: KProperty1<T, R>): IQueryColumnsApi {
        val ownerType = typedPropertyOwner(property)
        val outputName: String
        when {
            queryDeclarationOwner != null -> {
                require(ownerType == queryDeclarationOwner) {
                    "Typed output ${property.name} belongs to ${ownerType.qualifiedName}, not ${queryDeclarationOwner?.qualifiedName}"
                }
                outputName = QueryMetadataResolver.queryPublishedAlias(property)
            }
            queryDefinitionName != null -> {
                // The factory name is a runtime/CTE identity and may explicitly override QBQuery.name.
                // Resolve metadata for validation, but do not compare the logical Query name to that runtime name.
                resolveTypedRelationProperty(property)
                outputName = QueryMetadataResolver.queryPublishedAlias(property)
            }
            ICteDeclaration::class.java.isAssignableFrom(ownerType.java) -> {
                val (canonicalRelationName, publishedAlias) = querySourceContext.validateTypedOutput(ownerType, property, null)
                require(canonicalRelationName.isNotBlank()) { "Typed relation alias owner name must not be blank" }
                outputName = publishedAlias
            }
            else -> throw IllegalArgumentException("Typed CTE alias requires an ICteDeclaration property owner")
        }
        explicitAliasWasSet = true
        ast.ColumnAlias = outputName
        val isPropertyBacked = ast.ColumnMethod == null
        ast.PropertyReference = property.takeIf { isPropertyBacked }
        if (isPropertyBacked) {
            inferExecutionType(property)
        } else {
            clearInferredExecutionType()
        }
        return this
    }

    private fun <T, R> resolveTypedRelationProperty(property: KProperty1<T, R>): String {
        val ownerType = typedPropertyOwner(property)
        return QueryMetadataResolver.queryName(ownerType)
    }

    private fun <T, R> typedPropertyOwner(property: KProperty1<T, R>): KClass<*> {
        val instanceParameter = property.parameters.singleOrNull()
            ?.takeIf { it.kind == KParameter.Kind.INSTANCE }
            ?: throw IllegalArgumentException("Typed relation output must be a member property reference")
        val ownerType = instanceParameter.type.classifier as? KClass<*>
            ?: throw IllegalArgumentException("Cannot resolve the owner type of typed relation output ${property.name}")
        return ownerType
    }


}
