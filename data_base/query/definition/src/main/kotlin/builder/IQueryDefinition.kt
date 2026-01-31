package gog.my_project.data_base.query.definition.interfaces

import gog.my_project.data_base.query.definition.interfaces.join.IQueryDefinitionJoinsConnect
import gog.my_project.data_base.query.definition.interfaces.options.IQueryDefinitionOptionGroup
import gog.my_project.data_base.query.definition.interfaces.options.IQueryDefinitionOptionLimit
import gog.my_project.data_base.query.definition.interfaces.options.IQueryDefinitionOptionOffset
import gog.my_project.data_base.query.definition.interfaces.options.IQueryDefinitionOptionOrder
import gog.my_project.data_base.query.definition.interfaces.select.IQueryDefinitionSelect
import gog.my_project.data_base.query.definition.interfaces.table.IQueryDefinitionTable
import gog.my_project.data_base.query.definition.interfaces.where.IQueryDefinitionWhere
import gog.my_project.data_base.query.definition.interfaces.with.IQueryDefinitionWithsCollection


interface IQueryDefinition {

    fun withs(blockWiths: IQueryDefinitionWithsCollection.() -> IQueryDefinitionWithsCollection): IQueryDefinition;
    fun select(blockSelect: IQueryDefinitionSelect.() -> IQueryDefinitionSelect): IQueryDefinition;
    fun table(blockTable: IQueryDefinitionTable.() -> IQueryDefinitionTable): IQueryDefinition;
    fun joins(blockJoins: IQueryDefinitionJoinsConnect.() -> IQueryDefinitionJoinsConnect): IQueryDefinition;
    fun where(blockGroup: IQueryDefinitionWhere.() -> IQueryDefinitionWhere): IQueryDefinition;

    fun limit(blockLimit: IQueryDefinitionOptionLimit.() -> IQueryDefinitionOptionLimit) : IQueryDefinition ;
    fun offset(blockOffset: IQueryDefinitionOptionOffset.() -> IQueryDefinitionOptionOffset) : IQueryDefinition ;

    fun group(blockGroup: IQueryDefinitionOptionGroup.() -> IQueryDefinitionOptionGroup): IQueryDefinition;

    fun order(blockOrder: IQueryDefinitionOptionOrder.() -> IQueryDefinitionOptionOrder): IQueryDefinition;


}