package gog.my_project.data_base.query.renderer.interfaces

import gog.my_project.data_base.query.dialect.data_class.QueryDataClass
import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import kotlin.reflect.KClass

interface IRendererRegistry {

    val renderers : MutableMap<KClass<*>, IAstRenderer<*, *>>

    fun <T : Any , DC: QueryDataClass?> register(
        renderClass: KClass<T>,
        renderer:    IAstRenderer<T , DC>
    )

    fun <T: Any , DC: QueryDataClass?> get(type: KClass<T>): IAstRenderer<T , DC>?

    fun  render(
        ast:       Any?,
        dialect:   ISqlDialect,
        dataClass: QueryDataClass? = null
    ): String?

}