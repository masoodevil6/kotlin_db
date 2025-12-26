package gog.my_project.query.interfaces.sql_dialect

import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumns
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsIsConditions
import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsItem
import gog.my_project.query.interfaces.query_builders.tools.with.IQueryToolsWithItem


interface ISqlDialect {

    fun getBasicSql() : String


    fun getWithSql(withList:  List<IQueryToolsWithItem>) : String;

    fun getWithItemSql(withName : String, withBody: String) : String;





    fun getSelectSql(columnsList: List<IQueryToolsColumns>) : String;

    fun getColumnSql(columnName: String , columnMethod: String? , columnAlias: String?) : String;





    fun getTableSql(tableName: String , tableAlias: String?) : String;






    fun getJoinSql(joinList:  List<IQueryToolsJoinsItem>) : String

    fun getJoinItemSql(joinConnection: String , joinTable: String , joinAlias: String , joinCondition: String) : String





    fun getWhereSql(condition: String?) : String

    fun getOptionGroupSql(columnsList: List<String>) : String

    fun getOptionOderSql(columnsList: List<String> , orderType: String?) : String

    fun getOptionLimitSql(limitNumber: Int?) : String

    fun getOptionOffsetSql(offsetNumber: Int?) : String





    fun getConditionSql(conditionLogical: String, sideLeft: String? , conditionOperation: String , sideRight : String? , isAddLogical: Boolean = false) : String

    fun getConditionGroupSql(conditionLogical: String, conditions: MutableList<IQueryToolsIsConditions>, isAddLogical: Boolean = false) : String


}