package gog.my_project.data_base_schema.dialect.manager

import gog.my_project.data_base_schema.ast.interfaces.ISchemaAst
import gog.my_project.data_base_schema.dialect.interfaces.IRendererRegistry
import gog.my_project.data_base_schema.dialect.interfaces.ISqlDialect

abstract class BaseSqlDialect(
) : ISqlDialect {

    override val _registry: IRendererRegistry = RendererRegistry()

    init {
        registerRenders()
    }

    override fun <Ast : ISchemaAst<*>> render(
        ast: Ast
    ): String? {
        return _registry.render(ast , this , null);
    }

}
