package gog.my_project.query.sql_dialect


import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumns
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsIsConditions
import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsItem
import gog.my_project.query.interfaces.query_builders.tools.with.IQueryToolsWithItem
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.tools.templates.OTemplateSqlDialect

class MySqlDialect : ISqlDialect {

    override fun getBasicSql(): String {
        return "${OTemplateSqlDialect._TAG_TEMP_WITH} " +
                "${OTemplateSqlDialect._TAG_TEMP_SELECT} " +
                "${OTemplateSqlDialect._TAG_TEMP_TABLES} " +
                "${OTemplateSqlDialect._TAG_TEMP_JOINS} " +
                "${OTemplateSqlDialect._TAG_TEMP_WHERES}  " +
                "${OTemplateSqlDialect._TAG_TEMP_OPTION_GROUP} " +
                "${OTemplateSqlDialect._TAG_TEMP_OPTION_ORDER} " +
                "${OTemplateSqlDialect._TAG_TEMP_OPTION_LIMIT} " +
                "${OTemplateSqlDialect._TAG_TEMP_OPTION_OFFSET} ";
    }

    override fun getWithSql(withList:  List<IQueryToolsWithItem>) : String {
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

    override fun getWithItemSql(withName : String, withBody: String) : String {
        return " $withName AS  ($withBody) " ;
    }






    override fun getSelectSql(columnsList: List<IQueryToolsColumns>) : String {
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



    override fun getColumnSql(columnName: String , columnMethod: String? , columnAlias: String?) : String {

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







    override fun getTableSql(tableName: String , tableAlias: String?) : String{
        var queryTemp: String = " FROM "
        queryTemp += " $tableName ";
        if (tableAlias != null) {
            queryTemp += " As ${tableAlias} ";
        }

        return queryTemp;
    }







    override fun getJoinSql(joinList:  List<IQueryToolsJoinsItem>) : String {
        if (joinList != null && joinList.size > 0) {

            var joinsStr = "";
            for ((index, join) in joinList.withIndex()){
                joinsStr +=  "${join.toSql()} ";
            }
            return " $joinsStr ";
        }
        return "";
    }


    override fun getJoinItemSql(joinConnection: String , joinTable: String , joinAlias: String , joinCondition: String) : String {
        return " $joinConnection $joinTable AS $joinAlias ON $joinCondition " ;
    }






    override fun getWhereSql(condition: String?) : String {
        if (condition != null) {
            return " WHERE $condition " ;
        }
        return "";
    }


    override fun getOptionGroupSql(columnsList: List<String>) : String {
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



    override fun getOptionOderSql(columnsList: List<String> , orderType: String?) : String {
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


    override fun getOptionLimitSql(limitNumber: Int?) : String {
        if (limitNumber != null) {
            return " LIMIT $limitNumber "
        }
        return ""
    }


    override fun getOptionOffsetSql(offsetNumber: Int?) : String {
        if (offsetNumber != null) {
            return " OFFSET $offsetNumber "
        }
        return "";
    }




    override fun getConditionSql(conditionLogical: String, sideLeft: String? , conditionOperation: String , sideRight : String? , isAddLogical: Boolean) : String {
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

    override fun getConditionGroupSql(conditionLogical: String, conditions: MutableList<IQueryToolsIsConditions>, isAddLogical: Boolean) : String {
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