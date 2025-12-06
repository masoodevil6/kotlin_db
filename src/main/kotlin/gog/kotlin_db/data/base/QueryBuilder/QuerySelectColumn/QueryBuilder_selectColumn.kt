package gog.kotlin_db.data.base.QueryBuilder.QuerySelectColumn

import gog.kotlin_db.data.base.QueryBuilder.IQueryBuilder
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder
import gog.kotlin_db.data.base.QueryBuilder.QueryColumn.QueryBuilder_column

class QueryBuilder_selectColumn(

): IQueryBuilder {

    private val tableSelectTag = "{{TABLE_SELECT_TAG}}"

    protected var selectColumns: MutableList<QueryBuilder_column> = mutableListOf()


    fun addToSelectColumn(column: QueryBuilder_column) {
        selectColumns.add(column);
    }


    override fun getBaseTempSql(): String? {
        return " select $tableSelectTag " ;
    }

    override fun toSql(): String? {
        if (selectColumns != null && selectColumns.size > 0) {
            var queryTemp = getBaseTempSql();

            var selects = "";
            for ((index, column) in selectColumns.withIndex()){
                selects +=  " ${column.toSql()}";
                if (index < selectColumns.size - 1){
                    selects += ","
                }
            }
            return queryTemp?.replace(tableSelectTag, selects);
        }
        return null;
    }

    override fun replaceInBaseTemp(query: String): String {
        val querySelect= toSql();
        return query.replace(QueryBuilder._TAG_TEMP_SELECT, querySelect ?: "");
    }


}