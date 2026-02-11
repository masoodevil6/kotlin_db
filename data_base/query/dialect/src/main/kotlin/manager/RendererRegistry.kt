package gog.my_project.data_base.query.renderer.manager

import gog.my_project.data_base.query.dialect.data_class.QueryDataClass
import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer
import gog.my_project.data_base.query.renderer.interfaces.IRendererRegistry
import kotlin.reflect.KClass

class RendererRegistry : IRendererRegistry {

    override val renderers = mutableMapOf<KClass<*>, IAstRenderer<* , *>>()

    override fun <T : Any, DC : QueryDataClass?> register(
        renderClass: KClass<T>,
        renderer: IAstRenderer<T, DC>
    ) {
        renderers[renderClass] = renderer;
    }

    override fun <T : Any, DC : QueryDataClass?> get(type: KClass<T>): IAstRenderer<T, DC>? {
        return renderers[type] as? IAstRenderer<T , DC>;
    }



    @Suppress("UNCHECKED_CAST")
    override fun render(
        ast:       Any? ,
        dialect:   ISqlDialect ,
        dataClass: QueryDataClass?
    ): String?
    {
        if(ast==null) return "" ;

        val renderer = renderers[ast::class] ?: error("No renderer registered for ${ast::class}")

        return (renderer as IAstRenderer<Any , QueryDataClass?>)
            .render(
                ast ,
                RenderContext(dialect, this),
                dataClass
            );
    }


}