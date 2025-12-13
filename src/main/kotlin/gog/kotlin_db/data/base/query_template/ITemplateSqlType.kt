package gog.kotlin_db.data.base.query_template

import gog.kotlin_db.data.base.query_builder.tools.column.QueryToolsColumns
import gog.kotlin_db.data.base.query_builder.tools.conditions.IQueryToolsIsConditions
import gog.kotlin_db.data.base.query_builder.tools.conditions.QueryConditionGroups.QueryToolsConditionsGroups
import gog.kotlin_db.data.base.query_builder.tools.join.itemJoin.QueryToolsJoinsItem
import gog.kotlin_db.data.base.query_builder.tools.with.itemWith.QueryToolsWithItem

interface ITemplateSqlType {

    fun getBasicSql() : String




    fun getWithSql(
        withList:  List<QueryToolsWithItem>
    ) : String

    fun getWithItemSql(
        withName : String,
        withBody: String
    ) : String





    fun getSelectSql(
        columnsList: List<QueryToolsColumns>
    ) : String

    fun getColumnSql(
        columnName: String ,
        columnMethod: String? ,
        columnAlias: String?
    ) : String





    fun getTableSql(
        tableName: String ,
        tableAlias: String?
    ) : String






    fun getJoinSql(
        joinList:  List<QueryToolsJoinsItem>
    ) : String

    fun getJoinItemSql(
        joinConnection: String ,
        joinTable: String ,
        joinAlias: String ,
        joinCondition: String
    ) : String





    fun getWhereSql(
        condition: String?
    ) : String

    fun getOptionGroupSql(
        columnsList: List<String>
    ) : String

    fun getOptionOderSql(
        columnsList: List<String> ,
        orderType: String?
    ) : String

    fun getOptionLimitSql(
        limitNumber: Int?
    ) : String

    fun getOptionOffsetSql(
        offsetNumber: Int?
    ) : String





    fun getConditionSql(
        conditionLogical: String,
        sideLeft: String? ,
        conditionOperation: String ,
        sideRight : String? ,
        isAddLogical: Boolean = false
    ) : String

    fun getConditionGroupSql(
        conditionLogical: String ,
        conditions: MutableList<IQueryToolsIsConditions> ,
        isAddLogical: Boolean = false
    ) : String


}