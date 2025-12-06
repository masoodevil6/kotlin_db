package gog.kotlin_db.data.base.QueryBuilder.tools.options.OptionOrder

import gog.kotlin_db.data.base.QueryBuilder.tools.options.QueryTools_options

class QueryTools_optionOrder(

):
    IQueryTools_optionOrder
{

    protected var _orderByList: MutableList<String> = mutableListOf();
    private var _orderType : String? = " asc ";






    override fun addColumnOrder(columnName: String) {
        this._orderByList.add(columnName);
    }

    override fun setTypeOrder(orderType: String) {
        this._orderType= orderType;
    }









    override fun getBaseTempSql(): String? {
        return " order by ${QueryTools_options._TAG_TEMP_OPTION_ORDER_COLUMNS} ${QueryTools_options._TAG_TEMP_OPTION_ORDER_TYPE}"
    }

    override fun toSql(): String? {
        if (_orderByList.size > 0 && _orderType != ""){
            var queryTemp = getBaseTempSql();

            var optionOrderStr = "";
            for ((index, column) in _orderByList.withIndex()){
                optionOrderStr += column
            }
            queryTemp = queryTemp?.replace(QueryTools_options._TAG_TEMP_OPTION_ORDER_COLUMNS, optionOrderStr);

            queryTemp = queryTemp?.replace(QueryTools_options._TAG_TEMP_OPTION_ORDER_TYPE, _orderType ?: "");
            return queryTemp;
        }
        return null;
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryOption= toSql();
        return query.replace(QueryTools_options._TAG_TEMP_OPTION_ORDER, queryOption ?: "");
    }

}