package gog.my_project.data_base.migration.dialect.interfaces

import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import kotlin.reflect.KClass

interface IRendererRegistry {

    val renderers : MutableMap<KClass<*>, IAstRenderer<*, *>>

    fun <A: Any , D : MigrationDataClass?> register(
        renderClass: KClass<A>,
        renderer:    IAstRenderer<A , D>
    )

    fun <A: Any , D : MigrationDataClass?>  get(type: KClass<A>): IAstRenderer<A , D>?

    fun  render(
        ast:       Any?,
        dialect:   ISqlDialect,
        dataClass: MigrationDataClass? = null
    ): String?

}