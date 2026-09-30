package gog.my_project.data_base.query.renderer.manager

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.dialect.data_class.QueryDataClass
import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer
import gog.my_project.data_base.query.renderer.interfaces.IRendererRegistry
import kotlin.reflect.KClass

class RendererRegistry: IRendererRegistry {

    override val renderers = mutableMapOf<KClass<*>, IAstRenderer<* , *>>()

    override fun <A: Any , D : QueryDataClass?>  register(
        renderClass: KClass<A>,
        renderer: IAstRenderer<A, D>
    ) {
        renderers[renderClass] = renderer;
    }


    override fun <A: Any , D : QueryDataClass?> get(type: KClass<A>): IAstRenderer<A, D>? {
        return renderers[type] as? IAstRenderer<A , D>;
    }


    @Suppress("UNCHECKED_CAST")
    override fun render(
        ast:       Any? ,
        dialect:   ISqlDialect ,
        dataClass: QueryDataClass?
    ): String?
    {
        if(ast==null) return "" ;

        val rendererEntry = renderers.entries.firstOrNull{
            it.key.java.isAssignableFrom(ast::class.java)
        } ?: error("No renderer registered for ${ast::class}")

        val renderer = rendererEntry.value as IAstRenderer<Any , QueryDataClass?>

        return renderer.render(
                ast ,
                RenderContext(dialect, this),
                dataClass
            );
    }


}