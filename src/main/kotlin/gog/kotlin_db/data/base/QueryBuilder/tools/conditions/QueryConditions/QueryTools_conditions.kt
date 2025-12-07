package gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditions

import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools
import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.IQueryTools_conditions

class QueryTools_conditions(
    val conditionLogical: String,
    val sideLeft: String ,
    val conditionOperation: String ,
    val sideRight : String ,
):
    IQueryTools_conditionsMethods ,
    IQueryTools_conditions ,
    IQueryTools {



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


        /*----------------
           Logical
        ----------------*/
        const val _LOGICAL_AND =   "and"
        const val _LOGICAL_ON =    "on"
        const val _LOGICAL_OR  =   "or"


        /*----------------
           Operation
        ----------------*/
        const val _OPERATION_EQUALS =               "="
        const val _OPERATION_NOT_EQUALS =           "<>"
        const val _OPERATION_GEATER_THAN =          ">"
        const val _OPERATION_LESS_THAN =            "<"
        const val _OPERATION_GEATER_OR_EQUAL_THAN = ">="
        const val _OPERATION_LESS_OR_EQUAL_THAN =   "<="
        const val _OPERATION_LIKE =                 "like"
        const val _OPERATION_IN =                   "IN"
        const val _OPERATION_BETWEEN =              "between"



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