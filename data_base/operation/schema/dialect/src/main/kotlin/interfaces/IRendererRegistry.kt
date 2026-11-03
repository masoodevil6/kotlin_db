package gog.my_project.data_base_schema.dialect.interfaces

import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import kotlin.reflect.KClass

interface IRendererRegistry {

    val renderers : MutableMap<KClass<*>, IAstRenderer<*, *>>

    fun <A: Any , D : SchemaDataClass?> register(
        renderClass: KClass<A>,
        renderer:    IAstRenderer<A , D>
    )

    fun <A: Any , D : SchemaDataClass?>  get(type: KClass<A>): IAstRenderer<A , D>?

    fun  render(
        ast:       Any?,
        dialect:   ISqlDialect,
        dataClass: SchemaDataClass? = null
    ): String?

}