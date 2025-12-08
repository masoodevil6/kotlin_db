package gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditions

class QueryTools_conditions(
    val conditionLogical: String,
    val sideLeft: String ,
    val conditionOperation: String ,
    val sideRight : String ,
):
    IQueryTools_conditions
{



    public var isAddLogical: Boolean = false;




    override fun toWhereSql(isAddLogical: Boolean): String? {
        this.isAddLogical = isAddLogical;
        return toSql();
    }


    companion object {

        /*----------------
          Templates
        ----------------*/
        const val _TAG_TEMP_CONDITION=             "{{_TAG_TEMP_CONDITION}}"
        const val _TAG_TEMP_CONDITION_LOGICAL=     "{{_TAG_TEMP_CONDITION_LOGICAL}}"
        const val _TAG_TEMP_CONDITION_LEFT=        "{{_TAG_TEMP_CONDITION_LEFT}}"
        const val _TAG_TEMP_CONDITION_OPERATION=   "{{_TAG_TEMP_CONDITION_OPERATION}}"
        const val _TAG_TEMP_CONDITION_RIGHT=       "{{_TAG_TEMP_CONDITION_RIGHT}}"

    }



    override fun getBaseTempSql(): String? {
        return " $_TAG_TEMP_CONDITION_LOGICAL ($_TAG_TEMP_CONDITION_LEFT) $_TAG_TEMP_CONDITION_OPERATION ($_TAG_TEMP_CONDITION_RIGHT)"
    }

    override fun toSql(): String? {
        var queryTemp = getBaseTempSql();
        queryTemp =  queryTemp?.replace(_TAG_TEMP_CONDITION_LOGICAL,  if (isAddLogical) {conditionLogical} else {""});
        queryTemp =  queryTemp?.replace(_TAG_TEMP_CONDITION_LEFT, sideLeft);
        queryTemp =  queryTemp?.replace(_TAG_TEMP_CONDITION_OPERATION, conditionOperation);
        queryTemp =  queryTemp?.replace(_TAG_TEMP_CONDITION_RIGHT, sideRight);
        return queryTemp;
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }


}