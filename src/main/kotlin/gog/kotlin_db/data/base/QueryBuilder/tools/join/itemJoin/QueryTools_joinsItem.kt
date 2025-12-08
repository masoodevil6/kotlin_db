package gog.kotlin_db.data.base.QueryBuilder.tools.join.itemJoin

class QueryTools_joinsItem(
    val joinConnection: String ,
    val joinTable: String ,
    val joinAlias: String ,
    val joinConditions: String
) :
    IQueryTools_joinsItem
{

    companion object {
        const val _TAG_TEMP_ITEM_JOIN=               "{{_TAG_TEMP_ITEM_JOIN}}"
        const val _TAG_TEMP_ITEM_JOIN_CONNECTION=    "{{_TAG_TEMP_ITEM_JOIN_CONNECTION}}"
        const val _TAG_TEMP_ITEM_JOIN_TABLE=         "{{_TAG_TEMP_ITEM_JOIN_TABLE}}"
        const val _TAG_TEMP_ITEM_JOIN_ALIAS=         "{{_TAG_TEMP_ITEM_JOIN_ALIAS}}"
        const val _TAG_TEMP_ITEM_JOIN_CONDITION=     "{{_TAG_TEMP_ITEM_JOIN_CONDITION}}"

    }


    override fun getBaseTempSql(): String? {
        return " $_TAG_TEMP_ITEM_JOIN_CONNECTION $_TAG_TEMP_ITEM_JOIN_TABLE $_TAG_TEMP_ITEM_JOIN_ALIAS on $_TAG_TEMP_ITEM_JOIN_CONDITION" ;
    }

    override fun toSql(): String? {
        var queryTemp = getBaseTempSql();
        queryTemp =  queryTemp?.replace(_TAG_TEMP_ITEM_JOIN_CONNECTION, joinConnection);
        queryTemp =  queryTemp?.replace(_TAG_TEMP_ITEM_JOIN_TABLE,      joinTable);
        queryTemp =  queryTemp?.replace(_TAG_TEMP_ITEM_JOIN_ALIAS,      joinAlias);
        queryTemp =  queryTemp?.replace(_TAG_TEMP_ITEM_JOIN_CONDITION,  joinConditions);
        return queryTemp;
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }


}