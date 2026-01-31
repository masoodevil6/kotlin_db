package gog.my_project.data_base.query.definition.interfaces.options

import gog.my_project.data_base.query.definition.interfaces.column.IQueryDefinitionColumnsBase

interface IQueryDefinitionOptionOrder {

    fun orderAsc() : IQueryDefinitionOptionOrder;
    fun orderDesc() : IQueryDefinitionOptionOrder;

    fun addColumn(blockColumn: IQueryDefinitionColumnsBase.() -> IQueryDefinitionColumnsBase): IQueryDefinitionOptionOrder

}