package gog.my_project.data_base.query.builder.metadata.queries

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.core.annotations.models.QB_METADATA_UNSET
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/** Resolves Query declaration and output metadata one requested field at a time. */
object QueryMetadataResolver {

    fun queryName(type: KClass<*>): String = when (val value = queryAnnotation(type)?.name ?: QB_METADATA_UNSET) {
        QB_METADATA_UNSET -> classConvention(type, "Query")
        "" -> throw IllegalArgumentException("@QBQuery on ${type.qualifiedName} must declare a non-empty query name")
        else -> value
    }

    fun queryAliasFromModel(type: KClass<*>): String? = classConventionOrNull(type, "Query")

    fun queryAliasFromAnnotation(type: KClass<*>): String? =
        when (val value = queryAnnotation(type)?.alias ?: QB_METADATA_UNSET) {
            QB_METADATA_UNSET -> null
            "" -> throw IllegalArgumentException("@QBQuery on ${type.qualifiedName} must declare a non-empty query alias")
            else -> value
        }

    fun queryAlias(type: KClass<*>): String =
        queryAliasFromAnnotation(type) ?: queryAliasFromModel(type)
        ?: classConvention(type, "Query")

    fun queryLogicalOutputName(property: KProperty1<*, *>): String =
        when (val value = querySelectAnnotation(property)?.name ?: QB_METADATA_UNSET) {
            QB_METADATA_UNSET -> snakeCase(property.name)
            "" -> throw IllegalArgumentException(
                "@QBQuerySelect on ${property.name} must declare a non-empty logical name"
            )
            else -> value
        }

    fun queryPublishedAliasFromModel(property: KProperty1<*, *>): String = snakeCase(property.name)

    fun queryPublishedAliasFromAnnotation(property: KProperty1<*, *>): String? =
        when (val value = querySelectAnnotation(property)?.alias ?: QB_METADATA_UNSET) {
            QB_METADATA_UNSET -> null
            "" -> throw IllegalArgumentException(
                "@QBQuerySelect on ${property.name} must declare a non-empty SQL alias"
            )
            else -> value
        }

    fun queryPublishedAlias(property: KProperty1<*, *>): String =
        queryPublishedAliasFromAnnotation(property) ?: queryPublishedAliasFromModel(property)

    private fun queryAnnotation(type: KClass<*>): QBQuery? =
        type.annotations.filterIsInstance<QBQuery>().singleOrNull()

    private fun querySelectAnnotation(property: KProperty1<*, *>): QBQuerySelect? =
        property.annotations.filterIsInstance<QBQuerySelect>().singleOrNull()

    private fun classConvention(type: KClass<*>, suffix: String): String {
        val simpleName = type.simpleName
            ?: throw IllegalArgumentException("Cannot derive metadata name for anonymous class ${type.qualifiedName}")
        val stem = simpleName.removeSuffix(suffix)
        require(stem.isNotEmpty()) { "Cannot derive metadata name from ${type.qualifiedName}" }
        return snakeCase(stem)
    }

    private fun classConventionOrNull(type: KClass<*>, suffix: String): String? {
        val simpleName = type.simpleName ?: return null
        val stem = simpleName.removeSuffix(suffix)
        if (stem.isEmpty()) return null
        return snakeCase(stem)
    }

    private fun snakeCase(value: String): String = value
        .replace(Regex("([A-Z]+)([A-Z][a-z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
        .replace(Regex("([a-z0-9])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
        .lowercase()
}
