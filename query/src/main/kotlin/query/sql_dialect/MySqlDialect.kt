package gog.my_project.query.sql_dialect

import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumns
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditions
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsIsConditions
import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsConnect
import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsItem
import gog.my_project.query.interfaces.query_builders.tools.select.IQueryToolsSelect
import gog.my_project.query.interfaces.query_builders.tools.table.IQueryToolsTable
import gog.my_project.query.interfaces.query_builders.tools.where.IQueryToolsWhere
import gog.my_project.query.interfaces.query_builders.tools.with.collections.IQueryToolsWithsCollection
import gog.my_project.query.interfaces.query_builders.tools.with.item.IQueryToolsWithItem
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

    override fun getWithSql(with: IQueryToolsWithsCollection, withPrefix: Boolean) : String {

        val withList:  List<IQueryToolsWithItem> = with.getListWiths();
        if (withList.size > 0) {
            var withStr = "";
            if (withPrefix){
                withStr = " WITH "
            }
            for ((index, with) in withList.withIndex()){
                val itemWith = this.getWithItemSql(with);
                if (itemWith != null) {
                    withStr +=  " ${itemWith}";
                    if (index < withList.size - 1){
                        withStr += ","
                    }
                }
            }
            return withStr ;
        }
        return "";
    }

    override fun getWithItemSql(with: IQueryToolsWithItem) : String? {

        val withName : String? = with.getWithName();
        val withBody: String? = with.getWithBody();
        if (withName != null && withBody != null) {
            return " $withName AS  ($withBody) " ;
        }
        return null ;
    }






    override fun getSelectSql(select: IQueryToolsSelect, withPrefix: Boolean) : String {
        val columnsList = select.getListColumns();
        if (columnsList.size > 0) {
            var selects = "";
            if (withPrefix){
                selects = "SELECT "
            }
            for ((index, column) in columnsList.withIndex()){
                val itemColumn = this.getColumnSql(column);
                if (itemColumn != null){
                    selects +=  " $itemColumn";
                    if (index < columnsList.size - 1){
                        selects += ","
                    }
                }
            }

            return " $selects "
        }
        return "";
    }

    override fun getColumnSql(column: IQueryToolsColumns) : String? {
        val columnMethod : String? = column.getColumnMethod();
        val columnName : String? = column.getColumnName();
        val columnAlias : String? = column.getColumnAlias();

        if (columnName != null){
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
        return null;
    }

    override fun getColumnBaseSql(column: IQueryToolsColumnsBase): String? {
        val columnPrefix = column.getColumnPrefix();
        val columnName = column.getColumnName();
        if (columnName != null){
            var column = ""
            if (columnPrefix != null) {
                column = " $columnPrefix.";
            }
            column += "$columnName ";
            return column;
        }
        return null;
    }





    override fun getTableSql(table: IQueryToolsTable , withPrefix: Boolean) : String{
        val tableName: String? = table.getTableName();
        val tableAlias: String? = table.getTableAlias();

        var queryTemp: String = "";
        if (tableName != null) {
            if (withPrefix){
                queryTemp = " FROM ";
            }
            queryTemp += " $tableName ";
            if (tableAlias != null) {
                queryTemp += " As ${tableAlias} ";
            }
        }

        return queryTemp;
    }







    override fun getJoinSql(join:  IQueryToolsJoinsConnect) : String {
        val joinList: List<IQueryToolsJoinsItem> = join.getListJoins();
        if (joinList.size > 0) {
            var joinsStr = "";
            for ((index, join) in joinList.withIndex()){
                joinsStr +=  "${this.getJoinItemSql(join)} ";
            }
            return " $joinsStr ";
        }
        return "";
    }


    override fun getJoinItemSql(join: IQueryToolsJoinsItem) : String {
        var joinType: String = join.getJoinType();
        var joinTable: IQueryToolsTable = join.getJoinTable();
        var joinCondition: IQueryToolsConditionsGroups = join.getJoinConditions();

        var joinTableStr = this.getTableSql(joinTable , false);
        var joinConditionsStr = this.getConditionGroupSql(joinCondition);

        return " $joinType $joinTableStr ON $joinConditionsStr " ;
    }






    override fun getWhereSql(condition: IQueryToolsWhere) : String {
        val conditionGroup = condition.getGroupCondition();
        if (conditionGroup != null) {
            return  " WHERE ${this.getConditionGroupSql(conditionGroup)} " ;
        }
        return  "" ;
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




    override fun getConditionSql(condition: IQueryToolsConditions)  : String {

        val conditionIsAddLogical: Boolean = condition.isAddLogical();
        val conditionLogical: String? = condition.getConditionLogical();
        val conditionSideLeft: String? = condition.getConditionSideLeft();
        val conditionOperation: String? = condition.getConditionOperation();
        val conditionSideRight: String? = condition.getConditionSideRight();

        if (conditionSideLeft != null && conditionSideRight != null) {
            var queryTemp = "";
            queryTemp += " ${if (conditionIsAddLogical) {conditionLogical} else {""}} "
            queryTemp += " $conditionSideLeft "
            queryTemp += " $conditionOperation "
            queryTemp += " $conditionSideRight "
            return queryTemp;
        }
        return "";
    }

    override fun getConditionGroupSql(group: IQueryToolsConditionsGroups): String {
        val conditionLogical: String? = group.getGroupLogical();
        val conditions: MutableList<IQueryToolsIsConditions> = group.getGroupConditions();
        val isAddLogical: Boolean = group.isAddLogical();

        if (conditions != null && conditions.size>0) {
            var queryTemp = "";

            if (isAddLogical){
                queryTemp += " $conditionLogical "
            }

            var conditionStr = "";
            for ((index, condition) in conditions.withIndex()){
                conditionStr += condition.setIsAddLogical(index > 0).toString()
            }
            queryTemp += " ($conditionStr) ";

            return queryTemp;
        }
        return "";
    }

}