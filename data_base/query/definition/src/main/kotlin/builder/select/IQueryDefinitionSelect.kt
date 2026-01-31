package gog.my_project.data_base.query.definition.interfaces.select

import gog.my_project.data_base.query.definition.interfaces.column.IQueryDefinitionColumns

interface IQueryDefinitionSelect  {

    fun addColumn(blockColumn: IQueryDefinitionColumns.() -> IQueryDefinitionColumns): IQueryDefinitionSelect;

}