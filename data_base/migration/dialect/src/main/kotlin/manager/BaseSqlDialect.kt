package gog.my_project.data_base.migration.dialect.manager

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import gog.my_project.data_base.migration.dialect.interfaces.IRendererRegistry
import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect

abstract class BaseSqlDialect(
) : ISqlDialect {

    override val _registry: IRendererRegistry = RendererRegistry()

    init {
        registerRenders()
    }


    override fun <Ast : IMigrationAst> render(
        ast: Ast
    ): String? {
        return _registry.render(ast , this , null);
    }


}