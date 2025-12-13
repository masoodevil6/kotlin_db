package gog.kotlin_db.data.base.query_template

import gog.kotlin_db.data.base.query_builder.tools.column.QueryToolsColumns
import gog.kotlin_db.data.base.query_builder.tools.conditions.IQueryToolsIsConditions
import gog.kotlin_db.data.base.query_builder.tools.join.itemJoin.QueryToolsJoinsItem
import gog.kotlin_db.data.base.query_builder.tools.with.itemWith.QueryToolsWithItem
import gog.kotlin_db.utils.params.ObjectSqlTypeTemplates

class TemplateSqlTypeMySql : ITemplateSqlType{

    override fun getBasicSql(): String {
        return "${ObjectSqlTypeTemplates._TAG_TEMP_WITH} " +
                "${ObjectSqlTypeTemplates._TAG_TEMP_SELECT} " +
                "${ObjectSqlTypeTemplates._TAG_TEMP_TABLES} " +
                "${ObjectSqlTypeTemplates._TAG_TEMP_JOINS} " +
                "${ObjectSqlTypeTemplates._TAG_TEMP_WHERES}  " +
                "${ObjectSqlTypeTemplates._TAG_TEMP_OPTION_GROUP} " +
                "${ObjectSqlTypeTemplates._TAG_TEMP_OPTION_ORDER} " +
                "${ObjectSqlTypeTemplates._TAG_TEMP_OPTION_LIMIT} " +
                "${ObjectSqlTypeTemplates._TAG_TEMP_OPTION_OFFSET} ";
    }

    override fun getWithSql(withList:  List<QueryToolsWithItem>): String {
        if (withList != null && withList.size > 0) {

            var withStr = "";
            for ((index, with) in withList.withIndex()){
                withStr +=  " ${with.toSql()}";
                if (index < withList.size - 1){
                    withStr += ","
                }
            }
            return " WITH $withStr " ;
        }
        return "";
    }

    override fun getWithItemSql(withName : String, withBody: String): String {
        return " $withName AS  ($withBody) " ;
    }


    override fun getSelectSql(columnsList: List<QueryToolsColumns>): String {
        if (columnsList != null && columnsList.size > 0) {
            var selects = "";
            for ((index, column) in columnsList.withIndex()){
                selects +=  " ${column.toSql()}";
                if (index < columnsList.size - 1){
                    selects += ","
                }
            }

            return " SELECT $selects "
        }
        return "";
    }



    override fun getColumnSql(
        columnName: String ,
        columnMethod: String? ,
        columnAlias: String?
    ): String {

        var queryTemp: String = ""
        if (columnMethod != null) {
            queryTemp += columnMethod;
        }
        queryTemp += columnName;
        if (columnAlias != null) {
            queryTemp += " As ${columnAlias} ";
        }

        return queryTemp;
    }




    override fun getTableSql(tableName: String , tableAlias: String?): String {
        var queryTemp: String = " FROM "
        queryTemp += " $tableName ";
        if (tableAlias != null) {
            queryTemp += " As ${tableAlias} ";
        }

        return queryTemp;
    }





    override fun getJoinSql(joinList:  List<QueryToolsJoinsItem>): String {
        if (joinList != null && joinList.size > 0) {

            var joinsStr = "";
            for ((index, join) in joinList.withIndex()){
                joinsStr +=  "${join.toSql()} ";
            }
            return " $joinsStr ";
        }
        return "";
    }


    override fun getJoinItemSql(joinConnection: String , joinTable: String , joinAlias: String , joinCondition: String): String {
        return " $joinConnection $joinTable AS $joinAlias ON $joinCondition " ;
    }



    override fun getWhereSql(condition: String?): String {
        if (condition != null) {
            return " WHERE $condition " ;
        }
        return "";
    }



    override fun getOptionGroupSql(columnsList: List<String>): String {
        if (columnsList.isNotEmpty()) {
            var optionGroupStr = "";
            for ((index, column) in columnsList.withIndex()){
                optionGroupStr += column

                if (index < columnsList.size - 1){
                    optionGroupStr += ","
                }
            }

            return " GROUP BY $optionGroupStr "
        }

        return "";
    }



    override fun getOptionOderSql(columnsList: List<String> , orderType: String?): String {
        if (columnsList.isNotEmpty()) {
            var optionOrderStr = "";
            for ((index, column) in columnsList.withIndex()){
                optionOrderStr += column
            }

            var strExt = " ORDER BY $optionOrderStr "
            if (orderType != null) {
                strExt += " $orderType ";
            }

            return strExt
        }

        return "";
    }




    override fun getOptionLimitSql(limitNumber: Int?): String {
        if (limitNumber != null) {
            return " LIMIT $limitNumber "
        }
        return ""
    }


    override fun getOptionOffsetSql(offsetNumber: Int?): String {
        if (offsetNumber != null) {
            return " OFFSET $offsetNumber "
        }
        return "";
    }




    override fun getConditionSql(
        conditionLogical: String,
        sideLeft: String? ,
        conditionOperation: String ,
        sideRight : String? ,
        isAddLogical: Boolean
    ): String {
        if (sideLeft != null && sideRight != null) {
            var queryTemp = "";
            queryTemp += " ${if (isAddLogical) {conditionLogical} else {""}} "
            queryTemp += " $sideLeft "
            queryTemp += " $conditionOperation "
            queryTemp += " $sideRight "
            return queryTemp;
        }
        return "";
    }

    override fun getConditionGroupSql(
        conditionLogical: String ,
        conditions: MutableList<IQueryToolsIsConditions> ,
        isAddLogical: Boolean
    ): String {
        if (conditions != null && conditions.size>0) {
            var queryTemp = "";

            if (isAddLogical){
                queryTemp += " $conditionLogical "
            }

            var conditionStr = "";
            for ((index, condition) in conditions.withIndex()){
                conditionStr += condition.toWhereSql(index > 0).toString()
            }
            queryTemp += " ($conditionStr) ";

            return queryTemp;
        }
        return "";
    }

}