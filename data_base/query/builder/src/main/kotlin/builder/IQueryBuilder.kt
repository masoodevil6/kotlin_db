package gog.my_project.data_base.query.builder.interfaces

import gog.my_project.data_base.query.builder.interfaces.join.IQueryToolsJoinsConnect
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionGroup
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionLimit
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionOffset
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionOrder
import gog.my_project.data_base.query.builder.interfaces.select.IQueryToolsSelect
import gog.my_project.data_base.query.builder.interfaces.table.IQueryToolsTable
import gog.my_project.data_base.query.builder.interfaces.where.IQueryToolsWhere
import gog.my_project.data_base.query.builder.interfaces.with.IQueryToolsWithsCollection
import gog.my_project.data_base.query.definition.interfaces.IQueryDefinition

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

}