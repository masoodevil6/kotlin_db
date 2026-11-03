package gog.my_project.data_base_schema.dialect.interfaces

import gog.my_project.data_base_schema.ast.interfaces.ISchemaAst


interface ISqlDialect {

    val _prefixCreateTable:   String;

    val _registry: IRendererRegistry;

    fun registerRenders();

    fun <Ast : ISchemaAst<*>> render(ast: Ast): String?;

}
