package gog.my_project.data_base.query.cte.mapper

import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.metadata.queries.QueryMetadataResolver
import gog.my_project.data_base.query.cte.declaration.CteDeclaration
import gog.my_project.data_base.query.executer.result.QueryRow
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/** Maps materialized SELECT output aliases to mutable `@QBQuerySelect` properties. */
object CteQueryRowMapper {
    private val mappings = ConcurrentHashMap<Class<*>, ClassMapping>()

    /** Creates and maps a fresh instance of [queryType] for [row]. */
    fun <T : CteDeclaration<*, *>> map(queryType: KClass<T>, row: QueryRow): T =
        queryType.java.cast(mapRuntimeClass(queryType.java, row))

    internal fun <P, Self : CteDeclaration<P, Self>> map(
        declaration: CteDeclaration<P, Self>,
        row: QueryRow,
    ): Self {
        val mapped = mapRuntimeClass(declaration.javaClass, row)
        if (mapped.javaClass != declaration.javaClass) {
            throw IllegalArgumentException(
                "Mapped CTE output type ${mapped.javaClass.name} does not match declaration type ${declaration.javaClass.name}",
            )
        }
        @Suppress("UNCHECKED_CAST")
        return mapped as Self
    }

    private fun mapRuntimeClass(type: Class<*>, row: QueryRow): Any {
        require(CteDeclaration::class.java.isAssignableFrom(type)) {
            "Output type ${type.name} must extend CteDeclaration"
        }
        val mapping = mappings.computeIfAbsent(type, ::inspect)
        val target = mapping.newInstance(type)

        for (property in mapping.properties) {
            val value = try {
                row.getValue(property.sqlAlias)
            } catch (failure: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Missing SELECT output alias '${property.sqlAlias}' for @QBQuerySelect property '${property.name}' on ${type.name}",
                    failure,
                )
            }

            validateValue(type, property, value)
            try {
                property.setter.set(target, value)
            } catch (failure: Exception) {
                throw IllegalArgumentException(
                    "Could not assign SELECT output alias '${property.sqlAlias}' to property '${property.name}' on ${type.name}",
                    unwrapInvocationFailure(failure),
                )
            }
        }
        return target
    }

    private fun inspect(type: Class<*>): ClassMapping {
        val queryType = type.kotlin
        val outputProperties = queryType.memberProperties.filter { property ->
            property.annotations.any { it is QBQuerySelect }
        }
        require(outputProperties.isNotEmpty()) {
            "${type.name} has no properties annotated with @QBQuerySelect"
        }

        val mappedProperties = outputProperties.map { property ->
            val mutableProperty = property as? KMutableProperty1<*, *>
                ?: throw IllegalArgumentException(
                    "@QBQuerySelect property '${property.name}' on ${type.name} must have a setter",
                )
            require(mutableProperty.setter.visibility == KVisibility.PUBLIC) {
                "@QBQuerySelect property '${property.name}' on ${type.name} must have a public setter"
            }
            try {
                mutableProperty.setter.isAccessible = true
            } catch (failure: Exception) {
                throw IllegalArgumentException(
                    "@QBQuerySelect setter '${property.name}' on ${type.name} is not accessible to typed row mapping",
                    failure,
                )
            }

            val dataType = when (property.returnType.classifier) {
                Int::class -> DataType.INT
                Long::class -> DataType.LONG
                String::class -> DataType.STRING
                else -> throw IllegalArgumentException(
                    "@QBQuerySelect property '${property.name}' on ${type.name} has unsupported type ${property.returnType}",
                )
            }
            @Suppress("UNCHECKED_CAST")
            val setter = mutableProperty as KMutableProperty1<Any, Any?>
            OutputProperty(
                name = property.name,
                sqlAlias = QueryMetadataResolver.queryPublishedAlias(property),
                dataType = dataType,
                nullable = property.returnType.isMarkedNullable,
                setter = setter,
            )
        }

        val duplicateAliases = mappedProperties.groupBy(OutputProperty::sqlAlias)
            .filterValues { it.size > 1 }
            .keys
        require(duplicateAliases.isEmpty()) {
            "@QBQuerySelect properties on ${type.name} publish duplicate SQL aliases: ${duplicateAliases.joinToString() }"
        }

        val constructor = try {
            type.getDeclaredConstructor()
        } catch (failure: NoSuchMethodException) {
            throw IllegalArgumentException(
                "${type.name} must provide a no-argument constructor for typed row mapping",
                failure,
            )
        }
        if (!constructor.canAccess(null) && !constructor.trySetAccessible()) {
            throw IllegalArgumentException(
                "No-argument constructor for ${type.name} is not accessible to typed row mapping",
            )
        }
        return ClassMapping(constructor, mappedProperties)
    }

    private fun validateValue(type: Class<*>, property: OutputProperty, value: Any?) {
        val expected = if (property.nullable) {
            "${property.dataType} or NULL"
        } else {
            "non-null ${property.dataType}"
        }
        if (value == null) {
            require(property.nullable) {
                "SELECT output alias '${property.sqlAlias}' for property '${property.name}' on ${type.name} expected $expected but was NULL"
            }
            return
        }

        val matches = when (property.dataType) {
            DataType.LONG -> value is Long
            DataType.INT -> value is Int
            DataType.STRING -> value is String
        }
        require(matches) {
            "SELECT output alias '${property.sqlAlias}' for property '${property.name}' on ${type.name} expected $expected but was ${value.javaClass.name}"
        }
    }

    private fun ClassMapping.newInstance(type: Class<*>): Any = try {
        constructor.newInstance()
    } catch (failure: Exception) {
        val cause = (failure as? InvocationTargetException)?.targetException ?: failure
        throw IllegalArgumentException(
            "Could not create typed row instance of ${type.name} using its no-argument constructor",
            cause,
        )
    }

    private fun unwrapInvocationFailure(failure: Exception): Throwable =
        (failure as? InvocationTargetException)?.targetException ?: failure

    private data class ClassMapping(
        val constructor: Constructor<*>,
        val properties: List<OutputProperty>,
    )

    private data class OutputProperty(
        val name: String,
        val sqlAlias: String,
        val dataType: DataType,
        val nullable: Boolean,
        val setter: KMutableProperty1<Any, Any?>,
    )
}
