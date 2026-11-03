package gog.my_project.data_base_schema.dialect.manager

import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer
import gog.my_project.data_base_schema.dialect.interfaces.IRendererRegistry
import gog.my_project.data_base_schema.dialect.interfaces.ISqlDialect
import kotlin.reflect.KClass

class RendererRegistry: IRendererRegistry {

    override val renderers = mutableMapOf<KClass<*>, IAstRenderer<*, *>>()

    override fun <A: Any , D : SchemaDataClass?>  register(
        renderClass: KClass<A>,
        renderer: IAstRenderer<A, D>
    ) {
        renderers[renderClass] = renderer;
    }


    override fun <A: Any , D : SchemaDataClass?> get(type: KClass<A>): IAstRenderer<A, D>? {
        return renderers[type] as? IAstRenderer<A , D>;
    }


    @Suppress("UNCHECKED_CAST")
    override fun render(
        ast:       Any?,
        dialect:   ISqlDialect,
        dataClass: SchemaDataClass?
    ): String?
    {
        if(ast==null) return "" ;

        val rendererEntry = renderers.entries.firstOrNull{
            it.key.java.isAssignableFrom(ast::class.java)
        } ?: error("No renderer registered for ${ast::class}")

        val renderer = rendererEntry.value as IAstRenderer<Any , SchemaDataClass?>

        return renderer.render(
                ast ,
                RenderContext(dialect, this),
                dataClass
            );
    }


}