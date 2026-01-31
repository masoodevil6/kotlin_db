package gog.my_project.data_base.query.definition.interfaces.options

import gog.my_project.data_base.query.definition.interfaces.column.IQueryDefinitionColumnsBase

interface IQueryDefinitionOptionGroup {

    fun addColumn(blockColumn: IQueryDefinitionColumnsBase.() -> IQueryDefinitionColumnsBase): IQueryDefinitionOptionGroup

}