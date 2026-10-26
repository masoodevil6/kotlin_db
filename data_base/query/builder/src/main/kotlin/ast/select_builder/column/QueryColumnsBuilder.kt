package gog.my_project.data_base.query.builder.ast.select_builder.column

import gog.my_project.data_base.query.api.interfaces.api.select_api.column.IQueryColumnsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.concat.IQueryColumnMethodConcatApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.avg.IQueryColumnMethodAvgApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.count.IQueryColumnMethodCountApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.max.IQueryColumnMethodMaxApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.min.IQueryColumnMethodMinApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.sum.IQueryColumnMethodSumApi
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.api.tools.enums.SqlMethodColumn
import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.ast.schema.select_schema.column.QueryColumnsAst
import gog.my_project.data_base.query.ast.schema.select_schema.column_method.concat.QueryColumnMethodConcatAst
import gog.my_project.data_base.query.ast.schema.select_schema.column_method.avg.QueryColumnMethodAvgAst
import gog.my_project.data_base.query.ast.schema.select_schema.column_method.count.QueryColumnMethodCountAst
import gog.my_project.data_base.query.ast.schema.select_schema.column_method.max.QueryColumnMethodMaxAst
import gog.my_project.data_base.query.ast.schema.select_schema.column_method.min.QueryColumnMethodMinAst
import gog.my_project.data_base.query.ast.schema.select_schema.column_method.sum.QueryColumnMethodSumAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteSourceContext
import gog.my_project.data_base.query.builder.ast.select_builder.column_base.QueryColumnsBaseBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.column_method.concat.QueryColumnMethodConcatBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.column_method.avg.QueryColumnMethodAvgBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.column_method.count.QueryColumnMethodCountBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.column_method.max.QueryColumnMethodMaxBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.column_method.min.QueryColumnMethodMinBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.column_method.sum.QueryColumnMethodSumBuilder
import gog.my_project.data_base.query.builder.metadata.models.ModelMetadataResolver
import gog.my_project.data_base.query.builder.metadata.queries.QueryMetadataResolver
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1

class QueryColumnsBuilder internal constructor(
    override var ast: IQueryColumnsAst,
    private val modelAliasContext: ModelTableAliasContext,
    private val querySourceContext: CteSourceContext,
    private val queryDefinitionName: String?,
    private val queryDeclarationOwner: KClass<*>?,
) : IQueryColumnsApi {

    constructor(
        ast: IQueryColumnsAst = QueryColumnsAst(),
    ) : this( ast, ModelTableAliasContext(), CteSourceContext(), null, null)

    private var explicitAliasWasSet = false
    internal val hasExplicitAlias: Boolean get() = explicitAliasWasSet
    private var explicitExecutionTypeWasSet = ast.metadata.ExecutionType != null


    /* ==============================================================
    structure [method]
    ============================================================== */

    override fun methodCount(blockMethodCount: IQueryColumnMethodCountApi.() -> Unit): IQueryColumnsApi {
        this.ast.metadata.ColumnMethod = SqlMethodColumn.Count.value;
        val columnMethodCountAst = QueryColumnMethodCountAst();
        QueryColumnMethodCountBuilder(
            columnMethodCountAst,
        ).apply(blockMethodCount)
        this.ast.ColumnMethodCount =columnMethodCountAst
        return this
    }

    override fun methodConcat(blockMethodConcat: IQueryColumnMethodConcatApi.() -> Unit): IQueryColumnsApi {
        this.ast.metadata.ColumnMethod = SqlMethodColumn.Concat.value;
        val columnMethodConcatAst = QueryColumnMethodConcatAst();
        QueryColumnMethodConcatBuilder(
            columnMethodConcatAst,
        ).apply(blockMethodConcat)
        this.ast.ColumnMethodConcat =columnMethodConcatAst
        return this
    }

    override fun methodMin(blockMethodMin: IQueryColumnMethodMinApi.() -> Unit): IQueryColumnsApi {
        this.ast.metadata.ColumnMethod = SqlMethodColumn.Min.value;
        val columnMethodMinAst = QueryColumnMethodMinAst();
        QueryColumnMethodMinBuilder(
            columnMethodMinAst,
        ).apply(blockMethodMin)
        this.ast.ColumnMethodMin =columnMethodMinAst
        return this
    }

    override fun methodMax(blockMethodMax: IQueryColumnMethodMaxApi.() -> Unit): IQueryColumnsApi {
        this.ast.metadata.ColumnMethod = SqlMethodColumn.Max.value;
        val columnMethodMaxAst = QueryColumnMethodMaxAst();
        QueryColumnMethodMaxBuilder(
            columnMethodMaxAst,
        ).apply(blockMethodMax)
        this.ast.ColumnMethodMax =columnMethodMaxAst
        return this
    }

    override fun methodSum(blockMethodSum: IQueryColumnMethodSumApi.() -> Unit): IQueryColumnsApi {
        this.ast.metadata.ColumnMethod = SqlMethodColumn.Sum.value
        val columnMethodSumAst = QueryColumnMethodSumAst()
        QueryColumnMethodSumBuilder(columnMethodSumAst).apply(blockMethodSum)
        this.ast.ColumnMethodSum = columnMethodSumAst
        return this
    }

    override fun methodAvg(blockMethodAvg: IQueryColumnMethodAvgApi.() -> Unit): IQueryColumnsApi {
        this.ast.metadata.ColumnMethod = SqlMethodColumn.Avg.value
        val columnMethodAvgAst = QueryColumnMethodAvgAst()
        QueryColumnMethodAvgBuilder(columnMethodAvgAst).apply(blockMethodAvg)
        this.ast.ColumnMethodAvg = columnMethodAvgAst
        return this
    }


    /* ==============================================================
    structure [Execute]
    ============================================================== */

    override fun execute(dataType: DataType): IQueryColumnsApi {
        ast.metadata.ExecutionType = dataType
        ast.metadata.IsExecutionTypeExplicit = true
        explicitExecutionTypeWasSet = true
        return this
    }



    /* ==============================================================
    structure [column]
    ============================================================== */

    override fun column(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnsApi {
        val baseAst = QueryColumnsBaseAst()
        val baseBuilder = QueryColumnsBaseBuilder(
            baseAst,
            modelAliasContext,
            querySourceContext,
        ).apply(blockColumn)
        this.ast.Column = baseAst
        this.ast.metadata.PropertyReference = null
        this.ast.metadata.logicalOutputName = null
        clearInferredExecutionType()
        if (!explicitAliasWasSet) {
            this.ast.metadata.ColumnAlias = null
        }

        val modelProperty = baseBuilder.modelProperty
        val cteProperty = baseBuilder.cteProperty
        when {
            modelProperty != null -> {
                val isPropertyBacked = ast.metadata.ColumnMethod == null
                ast.metadata.PropertyReference = modelProperty.takeIf { isPropertyBacked }
                ast.metadata.logicalOutputName = modelProperty.takeIf { isPropertyBacked }
                    ?.let(QueryMetadataResolver::queryLogicalOutputName)
                if (isPropertyBacked) {
                    inferExecutionType(modelProperty)
                } else {
                    clearInferredExecutionType()
                }
                if (!explicitAliasWasSet) {
                    ast.metadata.ColumnAlias = ModelMetadataResolver.columnAlias(modelProperty)
                }
            }
            cteProperty != null -> {
                val isPropertyBacked = ast.metadata.ColumnMethod == null
                ast.metadata.PropertyReference = cteProperty.takeIf { isPropertyBacked }
                ast.metadata.logicalOutputName = cteProperty.takeIf { isPropertyBacked }
                    ?.let(QueryMetadataResolver::queryLogicalOutputName)
                if (isPropertyBacked) {
                    inferExecutionType(cteProperty)
                } else {
                    clearInferredExecutionType()
                }
                if (!explicitAliasWasSet) {
                    ast.metadata.ColumnAlias = QueryMetadataResolver.queryPublishedAlias(cteProperty)
                        .takeIf { isPropertyBacked }
                }
            }
        }
        return this;
    }





    private fun inferExecutionType(property: KProperty1<*, *>) {
        if (explicitExecutionTypeWasSet) return

        val propertyType = property.returnType.classifier as? KClass<*>
        ast.metadata.ExecutionType = when (propertyType) {
            Int::class -> DataType.INT
            Long::class -> DataType.LONG
            String::class -> DataType.STRING
            else -> null
        }
    }

    private fun clearInferredExecutionType() {
        if (!explicitExecutionTypeWasSet) {
            ast.metadata.ExecutionType = null
        }
    }



    /* ==============================================================
    structure [alias]
    ============================================================== */
    override fun alias(alias: String): IQueryColumnsApi {
        explicitAliasWasSet = true
        this.ast.metadata.ColumnAlias = alias.takeIf { it.isNotEmpty() }
        this.ast.metadata.IsColumnAliasExplicit = true
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
        ast.metadata.ColumnAlias = outputName
        ast.metadata.IsColumnAliasExplicit = true
        val isPropertyBacked = ast.metadata.ColumnMethod == null
        ast.metadata.PropertyReference = property
        ast.metadata.logicalOutputName = QueryMetadataResolver.queryLogicalOutputName(property)
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
