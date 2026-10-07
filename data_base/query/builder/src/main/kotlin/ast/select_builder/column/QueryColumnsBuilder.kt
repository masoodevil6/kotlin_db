package gog.my_project.data_base.query.builder.ast.select_builder.column

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.column.IQueryColumnsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.relations.QueryRelation
import gog.my_project.data_base.query.api.interfaces.relations.IQueryRelation
import gog.my_project.data_base.query.api.tools.enums.SqlMethodColumn
import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.ast.schema.select_schema.column.QueryColumnsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.RelationSourceContext
import gog.my_project.data_base.query.builder.ast.select_builder.column_base.QueryColumnsBaseBuilder
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class QueryColumnsBuilder internal constructor(
    override var params: MutableList<SqlParameter<*>>,
    override var ast: IQueryColumnsAst,
    private val modelAliasContext: ModelTableAliasContext,
    private val relationSourceContext: RelationSourceContext,
    private val relationDefinitionName: String?,
    private val relationDeclarationOwner: KClass<*>?,
) : IQueryColumnsApi {

    constructor(
        params: MutableList<SqlParameter<*>> = mutableListOf(),
        ast: IQueryColumnsAst = QueryColumnsAst(),
    ) : this(params, ast, ModelTableAliasContext(), RelationSourceContext(), null, null)

    private var explicitAliasWasSet = false
    internal val hasExplicitAlias: Boolean get() = explicitAliasWasSet


    /* ==============================================================
    structure [method]
    ============================================================== */

    override fun method(method: SqlMethodColumn): IQueryColumnsApi {
        this.ast.ColumnMethod = method.value;
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
        if (!explicitAliasWasSet) {
            this.ast.ColumnAlias = null
        }
        return this;
    }

    override fun <T : IModelBase, R> column(
        table: KClass<T>,
        property: KProperty1<T, R>,
    ): IQueryColumnsApi {
        val tableMetadata = table.findAnnotation<QBTable>()
            ?: throw IllegalArgumentException("${table.qualifiedName} must be annotated with @QBTable")
        require(tableMetadata.name.isNotEmpty()) {
            "@QBTable on ${table.qualifiedName} must declare a non-empty table name"
        }

        val columnMetadata = property.findAnnotation<QBColumn>()
            ?: throw IllegalArgumentException("${property.name} on ${table.qualifiedName} must be annotated with @QBColumn")
        require(columnMetadata.name.isNotEmpty()) {
            "@QBColumn on ${table.qualifiedName}.${property.name} must declare a non-empty column name"
        }

        val baseAst = QueryColumnsBaseAst().apply {
            tableAlias = tableMetadata.alias
            column = columnMetadata.name
        }
        modelAliasContext.registerColumn(table, baseAst)
        this.ast.Column = baseAst

        if (!explicitAliasWasSet) {
            this.ast.ColumnAlias = columnMetadata.alias.takeIf { it.isNotEmpty() }
        }
        return this
    }

    override fun relationColumn(
        relation: QueryRelation,
        sqlOutputName: String,
    ): IQueryColumnsApi {
        val (alias, sqlName) = relationSourceContext.resolve(relation, sqlOutputName)
        ast.Column = QueryColumnsBaseAst().apply {
            cteAlias = alias
            select = sqlName
        }
        if (!explicitAliasWasSet) {
            ast.ColumnAlias = null
        }
        return this
    }

    override fun <T, R> relationColumn(
        property: KProperty1<T, R>,
        qualifier: String?,
    ): IQueryColumnsApi {
        val ownerType = typedPropertyOwner(property)
        val sqlOutputName = property.name
        val canonicalRelationName = if (IQueryRelation::class.java.isAssignableFrom(ownerType.java)) {
            null
        } else {
            resolveTypedRelationProperty(property).first
        }
        val resolvedRelationName = relationSourceContext.validateTypedOutput(
            ownerType,
            sqlOutputName,
            canonicalRelationName,
        )
        require(qualifier == null || qualifier.isNotBlank()) {
            "Relation source qualifier must not be blank"
        }
        ast.Column = QueryColumnsBaseAst().apply {
            cteAlias = qualifier ?: resolvedRelationName
            select = sqlOutputName
        }
        if (!explicitAliasWasSet) {
            ast.ColumnAlias = null
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
        val outputName = property.name
        when {
            relationDeclarationOwner != null -> {
                require(ownerType == relationDeclarationOwner) {
                    "Typed output ${property.name} belongs to ${ownerType.qualifiedName}, not ${relationDeclarationOwner?.qualifiedName}"
                }
            }
            relationDefinitionName != null -> {
                val canonicalRelationName = resolveTypedRelationProperty(property).first
                require(canonicalRelationName == relationDefinitionName) {
                    "Typed output ${property.name} belongs to relation $canonicalRelationName, not $relationDefinitionName"
                }
            }
            IQueryRelation::class.java.isAssignableFrom(ownerType.java) -> {
                val canonicalRelationName = relationSourceContext.validateTypedOutput(ownerType, outputName, null)
                require(canonicalRelationName.isNotBlank()) { "Typed relation alias owner name must not be blank" }
            }
            else -> throw IllegalArgumentException("Typed relation alias requires an IQueryRelation property owner")
        }
        explicitAliasWasSet = true
        ast.ColumnAlias = outputName
        return this
    }

    private fun <T, R> resolveTypedRelationProperty(property: KProperty1<T, R>): Pair<String, String> {
        val ownerType = typedPropertyOwner(property)
        val outputName = property.name
        require(outputName.isNotBlank()) { "Typed relation output name must not be blank" }

        val companionFromKotlinReflection = try {
            ownerType.companionObjectInstance
        } catch (_: Exception) {
            null
        }
        val companion = companionFromKotlinReflection ?: try {
            ownerType.java.getDeclaredField("Companion").apply { isAccessible = true }.get(null)
        } catch (_: Exception) {
            throw IllegalArgumentException("${ownerType.qualifiedName} must declare companion relationName")
        }

        val relationNameProperty = companion::class.memberProperties.singleOrNull { it.name == "relationName" }
            ?: throw IllegalArgumentException("${ownerType.qualifiedName} companion must declare relationName")
        val canonicalRelationName = try {
            relationNameProperty.isAccessible = true
            relationNameProperty.getter.call(companion) as? String
        } catch (cause: Exception) {
            throw IllegalArgumentException("Cannot read companion relationName for ${ownerType.qualifiedName}", cause)
        } ?: throw IllegalArgumentException("${ownerType.qualifiedName}.relationName must be a String")
        require(canonicalRelationName.isNotBlank()) {
            "${ownerType.qualifiedName}.relationName must not be blank"
        }
        return canonicalRelationName to outputName
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
