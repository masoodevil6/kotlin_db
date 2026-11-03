package gog.my_project.data_base_schema.api.interfaces

import gog.my_project.data_base_schema.ast.interfaces.ISchemaAst

interface ISchemaApi<A : ISchemaAst<*>> {

    var ast: A;
}
