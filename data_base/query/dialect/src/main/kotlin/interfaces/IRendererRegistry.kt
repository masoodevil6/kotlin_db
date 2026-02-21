package gog.my_project.data_base.query.renderer.interfaces

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.dialect.data_class.QueryDataClass
import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import kotlin.reflect.KClass

interface IRendererRegistry {

    val renderers : MutableMap<KClass<*>, IAstRenderer<*, *>>

    fun <A: Any , D : QueryDataClass?> register(
        renderClass: KClass<A>,
        renderer:    IAstRenderer<A , D>
    )

    fun <A: Any , D : QueryDataClass?>  get(type: KClass<A>): IAstRenderer<A , D>?

    fun  render(
        ast:       Any? ,
        dialect:   ISqlDialect,
        dataClass: QueryDataClass? = null
    ): String?

}