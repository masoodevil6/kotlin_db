package gog.my_project.data_base_schema.api.interfaces.drop_index

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.drop_index.ISchemaDropIndexAst

interface ISchemaDropIndexApi : ISchemaApi<ISchemaDropIndexAst> {
    fun tableName(table: String): ISchemaDropIndexApi
    fun name(name: String): ISchemaDropIndexApi
    fun ifExists(): ISchemaDropIndexApi
}
