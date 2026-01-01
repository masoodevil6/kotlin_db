package gog.my_project.query.interfaces.sql_dialect

import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumns
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditions
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups
import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsConnect
import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsItem
import gog.my_project.query.interfaces.query_builders.tools.select.IQueryToolsSelect
import gog.my_project.query.interfaces.query_builders.tools.table.IQueryToolsTable
import gog.my_project.query.interfaces.query_builders.tools.where.IQueryToolsWhere
import gog.my_project.query.interfaces.query_builders.tools.with.collections.IQueryToolsWithsCollection
import gog.my_project.query.interfaces.query_builders.tools.with.item.IQueryToolsWithItem


interface ISqlDialect {

    fun getBasicSql() : String


    fun getWithSql(with: IQueryToolsWithsCollection, withPrefix: Boolean = true) : String;

    fun getWithItemSql(with: IQueryToolsWithItem) : String?;





    fun getSelectSql(select: IQueryToolsSelect, withPrefix: Boolean=true) : String;

    fun getColumnSql(column: IQueryToolsColumns) : String?;

    fun getColumnBaseSql(column: IQueryToolsColumnsBase) : String?;





    fun getTableSql(table: IQueryToolsTable, withPrefix: Boolean=true) : String;






    fun getJoinSql(join:  IQueryToolsJoinsConnect) : String

    fun getJoinItemSql(join: IQueryToolsJoinsItem) : String





    fun getWhereSql(condition: IQueryToolsWhere) : String

    fun getOptionGroupSql(columnsList: List<String>) : String

    fun getOptionOderSql(columnsList: List<String> , orderType: String?) : String

    fun getOptionLimitSql(limitNumber: Int?) : String

    fun getOptionOffsetSql(offsetNumber: Int?) : String





    fun getConditionSql(condition: IQueryToolsConditions) : String

    fun getConditionGroupSql(group: IQueryToolsConditionsGroups) : String


}