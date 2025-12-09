package gog.kotlin_db.data.base.QueryBuilder.tools.options.OptionGroup

import gog.kotlin_db.data.base.QueryBuilder.tools.options.QueryTools_options

class QueryTools_optionGroup(

):
    IQueryTools_optionGroup
{

    protected var _groupByList: MutableList<String> = mutableListOf();








    override fun addColumnGroup(columnName: String) {
        _groupByList.add(columnName);
    }







    override fun getBaseTempSql(): String? {
        return " group by ${QueryTools_options._TAG_TEMP_OPTION_GROUP_COLUMNS} "
    }

    override fun toSql(): String? {
        if (_groupByList.size > 0){
            var queryTemp = getBaseTempSql();

            var optionGroupStr = "";
            for ((index, column) in _groupByList.withIndex()){
                optionGroupStr += column
            }
            queryTemp = queryTemp?.replace(QueryTools_options._TAG_TEMP_OPTION_GROUP_COLUMNS, optionGroupStr);

            return queryTemp;
        }
        return "";
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryOption= toSql();
        return query.replace(QueryTools_options._TAG_TEMP_OPTION_GROUP, queryOption ?: "");
    }


}