package gog.my_project.data_base.query.definition.interfaces.column

import gog.my_project.data_base.query.definition.tools.enums.SqlMethodColumn

interface IQueryDefinitionColumns {

    fun method(method: SqlMethodColumn) : IQueryDefinitionColumns;
    fun sum() : IQueryDefinitionColumns;
    fun count() : IQueryDefinitionColumns;
    fun avg() : IQueryDefinitionColumns;
    fun min() : IQueryDefinitionColumns;
    fun max() : IQueryDefinitionColumns;

    fun column( blockColumn: IQueryDefinitionColumnsBase.() -> IQueryDefinitionColumnsBase): IQueryDefinitionColumns;

    fun alias(alias: String): IQueryDefinitionColumns;

}