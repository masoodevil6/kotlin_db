package gog.my_project.data_base.query.meta_data.models

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.annotations.models.QB_METADATA_UNSET
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/** Resolves Model annotations and conventions one requested metadata field at a time. */
object ModelMetadataResolver {

    fun requireTable(type: KClass<*>) {
        tableName(type)
    }

    fun requireColumn(type: KClass<*>, property: KProperty1<*, *>) {
        columnName(type, property)
    }

    fun tableName(type: KClass<*>): String = when (val value = tableAnnotation(type).name) {
        QB_METADATA_UNSET -> classConvention(type, "Model")
        "" -> throw IllegalArgumentException("@QBTable on ${type.qualifiedName} must declare a non-empty table name")
        else -> value
    }

    fun tableAlias(type: KClass<*>): String = when (val value = tableAnnotation(type).alias) {
        QB_METADATA_UNSET -> classConvention(type, "Model")
        else -> value // An explicitly empty alias retains the no-alias meaning.
    }

    fun columnName(type: KClass<*>, property: KProperty1<*, *>): String =
        when (val value = columnAnnotation(type, property).name) {
            QB_METADATA_UNSET -> property.name
            "" -> throw IllegalArgumentException(
                "@QBColumn on ${type.qualifiedName}.${property.name} must declare a non-empty column name"
            )
            else -> value
        }

    fun columnAlias(property: KProperty1<*, *>): String = when (val value = propertyColumnAnnotation(property).alias) {
        QB_METADATA_UNSET, "" -> property.name
        else -> value
    }

    private fun tableAnnotation(type: KClass<*>): QBTable =
        type.annotations.filterIsInstance<QBTable>().singleOrNull()
            ?: throw IllegalArgumentException("${type.qualifiedName} must be annotated with @QBTable")

    private fun columnAnnotation(type: KClass<*>, property: KProperty1<*, *>): QBColumn =
        property.annotations.filterIsInstance<QBColumn>().singleOrNull()
            ?: throw IllegalArgumentException(
                "${property.name} on ${type.qualifiedName} must be annotated with @QBColumn"
            )

    private fun propertyColumnAnnotation(property: KProperty1<*, *>): QBColumn =
        property.annotations.filterIsInstance<QBColumn>().singleOrNull()
            ?: throw IllegalArgumentException("${property.name} must be annotated with @QBColumn")

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
