package gog.kotlin_db.data.base.QueryBuilder.tools.with.itemWith

class QueryTools_withItem(
    val withName : String,
    val withBody: String
) :
    IQueryTools_withItem
{

    companion object {
        const val _TAG_TEMP_WITH=          "{{_TAG_TEMP_WITH}}"
        const val _TAG_TEMP_WITH_NAME=     "{{_TAG_TEMP_WITH_NAME}}"
        const val _TAG_TEMP_WITH_BODY=     "{{_TAG_TEMP_WITH_BODY}}"
    }



    override fun getBaseTempSql(): String? {
        return " $_TAG_TEMP_WITH_NAME as  ($_TAG_TEMP_WITH_BODY) " ;
    }

    override fun toSql(): String? {
        var queryTemp = getBaseTempSql();
        queryTemp =  queryTemp?.replace(_TAG_TEMP_WITH_NAME, withName);
        queryTemp =  queryTemp?.replace(_TAG_TEMP_WITH_BODY, withBody);
        return queryTemp;
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }


}