package gog.my_project.data_base.query.builder.ast.select_builder.metadata

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.annotations.models.QB_METADATA_UNSET
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/** Internal field-by-field metadata resolution shared by the existing Model and Query builders. */
internal object QueryBuilderMetadata {

    fun requireTable(type: KClass<*>): QBTable {
        val annotation = type.annotations.filterIsInstance<QBTable>().singleOrNull()
            ?: throw IllegalArgumentException("${type.qualifiedName} must be annotated with @QBTable")
        resolveTableName(type, annotation)
        return annotation
    }

    fun requireColumn(type: KClass<*>, property: KProperty1<*, *>): QBColumn {
        val annotation = property.annotations.filterIsInstance<QBColumn>().singleOrNull()
            ?: throw IllegalArgumentException("${property.name} on ${type.qualifiedName} must be annotated with @QBColumn")
        resolveColumnName(type, property, annotation)
        return annotation
    }

    fun tableName(type: KClass<*>, annotation: QBTable): String = when (val value = annotation.name) {
        QB_METADATA_UNSET -> classConvention(type, "Model")
        "" -> throw IllegalArgumentException("@QBTable on ${type.qualifiedName} must declare a non-empty table name")
        else -> value
    }

    fun tableAlias(type: KClass<*>, annotation: QBTable): String = when (val value = annotation.alias) {
        QB_METADATA_UNSET -> classConvention(type, "Model")
        else -> value // Explicit empty preserves the existing no-alias meaning.
    }

    fun columnName(type: KClass<*>, property: KProperty1<*, *>, annotation: QBColumn): String =
        resolveColumnName(type, property, annotation)

    fun columnAlias(property: KProperty1<*, *>, annotation: QBColumn): String = when (val value = annotation.alias) {
        QB_METADATA_UNSET, "" -> property.name
        else -> value
    }

    fun queryName(type: KClass<*>, annotation: QBQuery? = type.annotations.filterIsInstance<QBQuery>().singleOrNull()): String =
        when (val value = annotation?.name ?: QB_METADATA_UNSET) {
            QB_METADATA_UNSET -> classConvention(type, "Query")
            "" -> throw IllegalArgumentException("@QBQuery on ${type.qualifiedName} must declare a non-empty query name")
            else -> value
        }

    fun queryAlias(type: KClass<*>, annotation: QBQuery? = type.annotations.filterIsInstance<QBQuery>().singleOrNull()): String =
        when (val value = annotation?.alias ?: QB_METADATA_UNSET) {
            QB_METADATA_UNSET -> classConvention(type, "Query")
            "" -> throw IllegalArgumentException("@QBQuery on ${type.qualifiedName} must declare a non-empty query alias")
            else -> value
        }

    fun queryLogicalOutputName(property: KProperty1<*, *>): String {
        val annotation = property.annotations.filterIsInstance<QBQuerySelect>().singleOrNull()
        return when (val value = annotation?.name ?: QB_METADATA_UNSET) {
            QB_METADATA_UNSET -> snakeCase(property.name)
            "" -> throw IllegalArgumentException("@QBQuerySelect on ${property.name} must declare a non-empty logical name")
            else -> value
        }
    }

    fun queryPublishedAlias(property: KProperty1<*, *>): String {
        val annotation = property.annotations.filterIsInstance<QBQuerySelect>().singleOrNull()
        return when (val value = annotation?.alias ?: QB_METADATA_UNSET) {
            QB_METADATA_UNSET -> snakeCase(property.name)
            "" -> throw IllegalArgumentException("@QBQuerySelect on ${property.name} must declare a non-empty SQL alias")
            else -> value
        }
    }

    private fun resolveTableName(type: KClass<*>, annotation: QBTable): String = tableName(type, annotation)

    private fun resolveColumnName(type: KClass<*>, property: KProperty1<*, *>, annotation: QBColumn): String =
        when (val value = annotation.name) {
            QB_METADATA_UNSET -> property.name
            "" -> throw IllegalArgumentException("@QBColumn on ${type.qualifiedName}.${property.name} must declare a non-empty column name")
            else -> value
        }

    private fun classConvention(type: KClass<*>, suffix: String): String {
        val simpleName = type.simpleName
            ?: throw IllegalArgumentException("Cannot derive metadata name for anonymous class ${type.qualifiedName}")
        val stem = simpleName.removeSuffix(suffix)
        require(stem.isNotEmpty()) { "Cannot derive metadata name from ${type.qualifiedName}" }
        return snakeCase(stem)
    }

    private fun snakeCase(value: String): String = value
        .replace(Regex("([A-Z]+)([A-Z][a-z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
        .replace(Regex("([a-z0-9])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
        .lowercase()
}
