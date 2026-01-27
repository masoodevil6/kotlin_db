package gog.my_project.data_base.query_builder.dialect.dialects

import gog.my_project.data_base.annotations.models.QBTable
import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.columns.IQueryToolsColumns
import gog.my_project.data_base.query_builder.query.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query_builder.query.interfaces.conditions.IQueryToolsConditions
import gog.my_project.data_base.query_builder.query.interfaces.conditions.IQueryToolsConditionsGroups
import gog.my_project.data_base.query_builder.query.interfaces.conditions.condition_in.IQueryToolsConditionsCollection
import gog.my_project.data_base.query_builder.query.interfaces.join.IQueryToolsJoinsConnect
import gog.my_project.data_base.query_builder.query.interfaces.join.IQueryToolsJoinsItem
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionGroup
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionLimit
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionOffset
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionOrder
import gog.my_project.data_base.query_builder.query.interfaces.select.IQueryToolsSelect
import gog.my_project.data_base.query_builder.query.interfaces.table.IQueryToolsTable
import gog.my_project.data_base.query_builder.query.interfaces.where.IQueryToolsWhere
import gog.my_project.data_base.query_builder.query.interfaces.with.IQueryToolsWithItem
import gog.my_project.data_base.query_builder.query.interfaces.with.IQueryToolsWithsCollection


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
            val table = column.getTable();
            val column = column.getColumn();

            if (column != null && table != null){
                var columnStr = ""
                if (table.alias != "") {
                    columnStr = " ${table.alias}.";
                }
                columnStr += "${column.name} ";
                return columnStr;
            }
        }
        return null;
    }




    override fun getTableSql(table: IQueryToolsTable?, withPrefix: Boolean) : String{
        if (table != null){
            val table: QBTable? = table.getTable();

            var queryTemp: String = "";
            if (table != null) {
                if (withPrefix){
                    queryTemp = " FROM ";
                }
                queryTemp += " ${table.name} ";
                if (table.alias != "") {
                    queryTemp += " As ${table.alias} ";
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
            val conditionStr = this.getConditionGroupSql(conditionGroup )
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






    override fun getConditionGroupSql(group: IQueryToolsConditionsGroups?, hasLogical: Boolean, forceHasLogical: Boolean ): String? {
        if (group != null){

            val groups = group.getGroupConditions();
            val groupLogical = if (hasLogical){group.getGroupLogical()}else{""}

            if (groups.isNotEmpty()) {

                var queryTemp = " $groupLogical (";

                for ((index, condition) in groups.withIndex()){
                    var hasLogical = false;
                    if (index > 0 ){
                        hasLogical = true;
                    }

                    var conditionString : String? = null;
                    if (condition is IQueryToolsConditions){
                        conditionString = this.getConditionSql(condition , hasLogical)
                    }
                    else if (condition is IQueryToolsConditionsGroups){
                        conditionString = this.getConditionGroupSql(condition , hasLogical)
                    }

                    if (conditionString != null){
                        queryTemp +=  " $conditionString ";
                    }
                }

                queryTemp += " ) ";

                return queryTemp;
            }
        }

        return null;
    }

    override fun getConditionSql(condition: IQueryToolsConditions?, hasLogical: Boolean )  : String? {
        if (condition != null){

            val conditionLogical = condition.getConditionLogical()?.value;
            val conditionSideLeft = condition.getConditionSideLeft();
            val conditionOperation: String? = condition.getConditionOperation()?.value;
            val conditionSideRight = condition.getConditionSideRight();

            val conditionSideLeftStr = this.getColumnBaseSql(conditionSideLeft);

            val conditionSideRightStr =
                when (conditionSideRight) {
                    is IQueryToolsColumnsBase ->  this.getColumnBaseSql(conditionSideRight);
                    is IQueryToolsConditionsCollection ->  this.getConditionCollectionSql(conditionSideRight);
                    is String -> conditionSideRight;
                    else -> null;
                };

            if (conditionSideLeftStr != null && conditionSideRightStr != null) {
                var queryTemp = "";

                if (conditionLogical != null && hasLogical){
                    queryTemp += conditionLogical
                }

                queryTemp += " $conditionSideLeftStr $conditionOperation $conditionSideRightStr  "
                return queryTemp;
            }
        }

        return null;
    }

    override fun getConditionCollectionSql(params: IQueryToolsConditionsCollection?): String? {
        val paramsIn = params?.getParamsCollection();
        if (paramsIn != null){
            var paramInStr = " ( ";
            for ((index, paramName) in paramsIn.withIndex()){
                paramInStr += " :$paramName ";
                if (index < paramsIn.size - 1){
                    paramInStr += ","
                }
            }
            paramInStr += " ) "
            return paramInStr;
        }

        return null;
    }

}