package gog.my_project.data_base.query.builder.metadata.models

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

    fun tableNameFromModel(type: KClass<*>): String? = classConventionOrNull(type, "Model")

    fun tableNameFromAnnotation(type: KClass<*>): String? = when (val value = tableAnnotation(type).name) {
        QB_METADATA_UNSET -> null
        "" -> throw IllegalArgumentException("@QBTable on ${type.qualifiedName} must declare a non-empty table name")
        else -> value
    }

    fun tableName(type: KClass<*>): String = tableNameFromAnnotation(type)
        ?: tableNameFromModel(type)
        ?: classConvention(type, "Model")

    fun tableAliasFromModel(type: KClass<*>): String? = classConventionOrNull(type, "Model")

    fun tableAliasFromAnnotation(type: KClass<*>): String? =
        tableAnnotation(type).alias.takeUnless { it == QB_METADATA_UNSET }

    fun tableAlias(type: KClass<*>): String = tableAliasFromAnnotation(type)
        ?: tableAliasFromModel(type)
        ?: classConvention(type, "Model")

    fun columnNameFromModel(property: KProperty1<*, *>): String = property.name

    fun columnNameFromAnnotation(type: KClass<*>, property: KProperty1<*, *>): String? =
        when (val value = columnAnnotation(type, property).name) {
            QB_METADATA_UNSET -> null
            "" -> throw IllegalArgumentException(
                "@QBColumn on ${type.qualifiedName}.${property.name} must declare a non-empty column name"
            )
            else -> value
        }

    fun columnName(type: KClass<*>, property: KProperty1<*, *>): String =
        columnNameFromAnnotation(type, property) ?: columnNameFromModel(property)

    fun columnAliasFromModel(property: KProperty1<*, *>): String = property.name

    fun columnAliasFromAnnotation(property: KProperty1<*, *>): String? =
        propertyColumnAnnotation(property).alias.takeUnless { it == QB_METADATA_UNSET || it.isEmpty() }

    fun columnAlias(property: KProperty1<*, *>): String =
        columnAliasFromAnnotation(property) ?: columnAliasFromModel(property)

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
        return classConventionOrNull(type, suffix)
            ?: throw IllegalArgumentException("Cannot derive metadata name from ${type.qualifiedName}")
    }

    private fun classConventionOrNull(type: KClass<*>, suffix: String): String? {
        val simpleName = type.simpleName
            ?: return null
        val stem = simpleName.removeSuffix(suffix)
        if (stem.isEmpty()) return null
        return snakeCase(stem)
    }

    private fun snakeCase(value: String): String = value
        .replace(Regex("([A-Z]+)([A-Z][a-z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
        .replace(Regex("([a-z0-9])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
        .lowercase()
}
