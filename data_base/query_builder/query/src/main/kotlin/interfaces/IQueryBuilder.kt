package gog.my_project.data_base.query_builder.query.interfaces

import gog.my_project.data_base.query_builder.query.interfaces.join.IQueryToolsJoinsConnect
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionGroup
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionLimit
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionOffset
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionOrder
import gog.my_project.data_base.query_builder.query.interfaces.select.IQueryToolsSelect
import gog.my_project.data_base.query_builder.query.interfaces.table.IQueryToolsTable
import gog.my_project.data_base.query_builder.query.interfaces.where.IQueryToolsWhere
import gog.my_project.data_base.query_builder.query.interfaces.with.IQueryToolsWithsCollection

interface IQueryBuilder: IQueryTools {

    fun getQueryWiths(): IQueryToolsWithsCollection?;
    fun getQuerySelect(): IQueryToolsSelect?;
    fun getQueryTable(): IQueryToolsTable?;
    fun getQueryJoins(): IQueryToolsJoinsConnect?;
    fun getQueryWhere(): IQueryToolsWhere?;
    fun getQueryOptionLimit(): IQueryToolsOptionLimit?;
    fun getQueryOptionOffset(): IQueryToolsOptionOffset?;
    fun getQueryOptionGroup(): IQueryToolsOptionGroup?;
    fun getQueryOptionOrder(): IQueryToolsOptionOrder?;

    fun withs(blockWiths: IQueryToolsWithsCollection.() -> IQueryToolsWithsCollection): IQueryBuilder;
    fun select(blockSelect: IQueryToolsSelect.() -> IQueryToolsSelect): IQueryBuilder;
    fun table(blockTable: IQueryToolsTable.() -> IQueryToolsTable): IQueryBuilder;
    fun joins(blockJoins: IQueryToolsJoinsConnect.() -> IQueryToolsJoinsConnect): IQueryBuilder;
    fun where(blockGroup: IQueryToolsWhere.() -> IQueryToolsWhere): IQueryBuilder;

//    fun pageInit(optionLimit: Long , optionOffset: Long) : IQueryBuilder;
//    fun limit(optionLimit: Long) : IQueryBuilder;
//    fun offset(optionOffset: Long) : IQueryBuilder;
//
    fun limit(blockLimit: IQueryToolsOptionLimit.() -> IQueryToolsOptionLimit) : IQueryBuilder ;
    fun offset(blockOffset: IQueryToolsOptionOffset.() -> IQueryToolsOptionOffset) : IQueryBuilder ;

    fun group(blockGroup: IQueryToolsOptionGroup.() -> IQueryToolsOptionGroup): IQueryBuilder;

    fun order(blockOrder: IQueryToolsOptionOrder.() -> IQueryToolsOptionOrder): IQueryBuilder;



}