package gog.my_project.query.sql_dialect

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumns
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditions
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsIsConditions
import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsConnect
import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsItem
import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionGroup
import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionLimit
import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionOffset
import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionOrder
import gog.my_project.query.interfaces.query_builders.tools.select.IQueryToolsSelect
import gog.my_project.query.interfaces.query_builders.tools.table.IQueryToolsTable
import gog.my_project.query.interfaces.query_builders.tools.where.IQueryToolsWhere
import gog.my_project.query.interfaces.query_builders.tools.with.collections.IQueryToolsWithsCollection
import gog.my_project.query.interfaces.query_builders.tools.with.item.IQueryToolsWithItem
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect

class MySqlDialect(

) : ISqlDialect {

    override fun getBasicSql(baseQuery: IQueryBuilder?): String? {
        if (baseQuery != null) {
            val queryBuilderWiths = baseQuery.getQueryWiths();
            val queryBuilderSelect = baseQuery.getQuerySelect();
            val queryBuilderTable = baseQuery.getQueryTable();
            val queryBuilderJoins = baseQuery.getQueryJoins();
            val queryBuilderWhere = baseQuery.getQueryWhere();
            val queryBuilderOptionLimit = baseQuery.getQueryOptionLimit();
            val queryBuilderOptionOffset = baseQuery.getQueryOptionOffset();
            val queryBuilderOptionGroup = baseQuery.getQueryOptionGroup();
            val queryBuilderOptionOrder = baseQuery.getQueryOptionOrder();

            return " ${this.getWithSql(queryBuilderWiths)} " +
                    " ${this.getSelectSql(queryBuilderSelect)} " +
                    " ${this.getTableSql(queryBuilderTable)} " +
                    " ${this.getJoinSql(queryBuilderJoins)} " +
                    " ${this.getWhereSql(queryBuilderWhere)} " +
                    " ${this.getOptionGroupSql(queryBuilderOptionGroup)} " +
                    " ${this.getOptionOderSql(queryBuilderOptionOrder)} " +
                    " ${this.getOptionLimitSql(queryBuilderOptionLimit)} " +
                    " ${this.getOptionOffsetSql(queryBuilderOptionOffset)} ";
        }
        return null;
    }

    override fun getWithSql(with: IQueryToolsWithsCollection?, withPrefix: Boolean) : String {

        if (with != null) {
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
        }
        return "";
    }

    override fun getWithItemSql(with: IQueryToolsWithItem?) : String? {
        if (with != null) {
            val withName : String? = with.getWithName();
            val withBody: IQueryBuilder? = with.getWithBody();
            val withBodyStr = this.getBasicSql(withBody);

            if (withName != null && withBodyStr != null) {
                return " $withName AS  ($withBodyStr) " ;
            }
        }
        return null ;
    }




    override fun getSelectSql(select: IQueryToolsSelect?, withPrefix: Boolean) : String {
        if (select != null){
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
        }
        return "";
    }

    override fun getColumnSql(column: IQueryToolsColumns?) : String? {
        if (column != null){
            val columnMethod : String? = column.getColumnMethod()?.value;
            val columnNameBase : IQueryToolsColumnsBase? = column.getColumnName();
            val columnAlias : String? = column.getColumnAlias();

            val columnName =  this.getColumnBaseSql(columnNameBase)
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
        }
        return null;
    }

    override fun getColumnBaseSql(column: IQueryToolsColumnsBase?): String? {
        if (column != null){
            val columnPrefix = column.getColumnPrefix();
            val columnName = column.getColumnName();
            val columnQuery = column.getColumnQuery();

            if (columnName != null){
                var column = ""
                if (columnPrefix != null) {
                    column = " $columnPrefix.";
                }
                column += "$columnName ";
                return column;
            }
            else if (columnQuery != null){
                val query = this.getBasicSql(columnQuery)
                if (query != null){
                    return " ($query) ";
                }
            }
        }
        return null;
    }




    override fun getTableSql(table: IQueryToolsTable? , withPrefix: Boolean) : String{
        if (table != null){
            val tableName: String? = table.getTableName();
            val tableAlias: String? = table.getTableAlias();
            val tableQuery: IQueryBuilder? = table.getTableQuery();

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
            else if (tableQuery != null){
                val query = this.getBasicSql(tableQuery);
                if (query != null){
                    if (withPrefix){
                        queryTemp = " FROM ";
                    }
                    queryTemp += " ($query) ";
                    if (tableAlias != null) {
                        queryTemp += " As ${tableAlias} ";
                    }
                }
            }

            return queryTemp;
        }

        return "";
    }




    override fun getJoinSql(join:  IQueryToolsJoinsConnect?) : String {
        if (join != null){
            val joinList: List<IQueryToolsJoinsItem> = join.getListJoins();
            if (joinList.size > 0) {
                var joinsStr = "";
                for ((index, join) in joinList.withIndex()){
                    val itemJoin = this.getJoinItemSql(join)
                    if (itemJoin != null){
                        joinsStr +=  " ${itemJoin} ";
                    }
                }
                return " $joinsStr ";
            }
        }
        return "";
    }

    override fun getJoinItemSql(join: IQueryToolsJoinsItem?) : String? {
        if (join != null){
            var joinType: String = join.getJoinType().value;
            var joinTable: IQueryToolsTable = join.getJoinTable();
            var joinCondition: IQueryToolsConditionsGroups = join.getJoinConditions();

            var joinTableStr = this.getTableSql(joinTable , false);
            var joinConditionsStr = this.getConditionGroupSql(joinCondition , true);

            return " $joinType $joinTableStr $joinConditionsStr " ;
        }
        return null;
    }




    override fun getWhereSql(condition: IQueryToolsWhere?) : String {
        if (condition != null){
            val conditionGroup = condition.getGroupCondition();
            val conditionStr = this.getConditionGroupSql(conditionGroup , null)
            if (conditionStr != null) {
                return  " WHERE $conditionStr " ;
            }
        }
        return  "" ;
    }




    override fun getOptionGroupSql(group: IQueryToolsOptionGroup?): String {
        if (group != null){
            val columnsList = group.getListColumns();
            if (columnsList.isNotEmpty()) {
                var optionGroupStr = "";
                for ((index, column) in columnsList.withIndex()){
                    val columnStr = this.getColumnBaseSql(column);
                    if (columnStr != null) {
                        optionGroupStr += " $columnStr"
                    }
                    if (index < columnsList.size - 1){
                        optionGroupStr += ","
                    }
                }

                return " GROUP BY $optionGroupStr "
            }
        }

        return "";
    }

    override fun getOptionOderSql(order: IQueryToolsOptionOrder?) : String {
        if (order != null){
            var columnsList = order.getListColumns();
            var orderType = order.getOrderType().value;

            if (columnsList.isNotEmpty()) {
                var optionOrderStr = "";
                for ((index, column) in columnsList.withIndex()){
                    val columnStr = this.getColumnBaseSql(column);
                    if (columnStr != null) {
                        optionOrderStr += " $columnStr"
                    }
                }

                var strExt = " ORDER BY $optionOrderStr "
                if (orderType != null) {
                    strExt += " $orderType ";
                }

                return strExt
            }
        }

        return "";
    }

    override fun getOptionLimitSql(limit: IQueryToolsOptionLimit?) : String {
        if (limit != null){
            val limitNumber = limit.getOptionLimit();
            if (limitNumber != null) {
                return " LIMIT $limitNumber "
            }
        }
        return ""
    }

    override fun getOptionOffsetSql(offset: IQueryToolsOptionOffset?): String {
        if (offset != null) {
            val offsetNumber = offset.getOptionOffset();
            if (offsetNumber != null) {
                return " OFFSET $offsetNumber "
            }
        }
        return "";
    }






    override fun getConditionSql(condition: IQueryToolsConditions?  , forceIsAddLogical: Boolean?)  : String? {
        if (condition != null){
            val conditionIsAddLogical: Boolean = condition.isAddLogical();
            val conditionLogical = condition.getConditionLogical()?.value;
            val conditionSideLeft = condition.getConditionSideLeft();
            val conditionOperation: String? = condition.getConditionOperation()?.value;
            val conditionSideRight = condition.getConditionSideRight();

            val conditionSideLeftStr = this.getColumnBaseSql(conditionSideLeft);
            val conditionSideRightStr = this.getColumnBaseSql(conditionSideRight);
            if (conditionSideLeftStr != null && conditionSideRightStr != null) {
                var queryTemp = "";

                if (conditionLogical != null){
                    if (forceIsAddLogical == null && conditionIsAddLogical){
                        queryTemp += conditionLogical
                    }
                    else if(forceIsAddLogical != null && forceIsAddLogical){
                        queryTemp += conditionLogical
                    }
                }

                queryTemp += " $conditionSideLeftStr "
                queryTemp += " $conditionOperation "
                queryTemp += " $conditionSideRightStr "
                return queryTemp;
            }
        }

        return null;
    }

    override fun getConditionGroupSql(group: IQueryToolsConditionsGroups?  , forceIsAddLogical: Boolean? ): String? {
        if (group != null) {
            val conditionLogical: String? = group.getGroupLogical()?.value;
            val conditions = group.getGroupConditions();
            val conditionIsAddLogical: Boolean = group.isAddLogical();

            if (conditions.size>0) {
                var queryTemp = "";

                if (conditionLogical != null){
                    if (forceIsAddLogical == null && conditionIsAddLogical){
                        queryTemp += conditionLogical
                    }
                    else if(forceIsAddLogical != null && forceIsAddLogical){
                        queryTemp += conditionLogical
                    }
                }

                for ((index, condition) in conditions.withIndex()){

                    if (condition is IQueryToolsConditions){
                        val conditionString = this.getConditionSql(condition, forceIsAddLogical)
                        if (conditionString != null){
                            queryTemp += conditionString;
                        }
                    }
                    else if (condition is IQueryToolsConditionsGroups){
                        val conditionString = this.getConditionGroupSql(condition , forceIsAddLogical)
                        if (conditionString != null){
                            queryTemp += " ($conditionString) ";
                        }
                    }
                }

                return queryTemp;
            }
        }

        return null;
    }

}