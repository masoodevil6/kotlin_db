package gog.my_project.data_base.migration.dialect.interfaces

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst


interface ISqlDialect {

    val _prefixCreateTable:   String;

    val _registry: IRendererRegistry;

    fun registerRenders();

    fun <Ast: IMigrationAst> render(ast: Ast) : String?;

}