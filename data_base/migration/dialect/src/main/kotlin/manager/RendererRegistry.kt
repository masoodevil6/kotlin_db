package gog.my_project.data_base.migration.dialect.manager

import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer
import gog.my_project.data_base.migration.dialect.interfaces.IRendererRegistry
import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect
import kotlin.reflect.KClass

class RendererRegistry: IRendererRegistry {

    override val renderers = mutableMapOf<KClass<*>, IAstRenderer<*, *>>()

    override fun <A: Any , D : MigrationDataClass?>  register(
        renderClass: KClass<A>,
        renderer: IAstRenderer<A, D>
    ) {
        renderers[renderClass] = renderer;
    }


    override fun <A: Any , D : MigrationDataClass?> get(type: KClass<A>): IAstRenderer<A, D>? {
        return renderers[type] as? IAstRenderer<A , D>;
    }


    @Suppress("UNCHECKED_CAST")
    override fun render(
        ast:       Any?,
        dialect:   ISqlDialect,
        dataClass: MigrationDataClass?
    ): String?
    {
        if(ast==null) return "" ;

        val rendererEntry = renderers.entries.firstOrNull{
            it.key.java.isAssignableFrom(ast::class.java)
        } ?: error("No renderer registered for ${ast::class}")

        val renderer = rendererEntry.value as IAstRenderer<Any , MigrationDataClass?>

        return renderer.render(
                ast ,
                RenderContext(dialect, this),
                dataClass
            );
    }


}