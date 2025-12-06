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




    private val _conditionLogicalTag =     "{{CONDITION_LOGICAL_TAG}}"
    private val _SideLeftTag =             "{{SIDE_LEFT_TAG}}"
    private val _conditionOperationTag =   "{{CONDITION_OPERATION_TAG}}"
    private val _SideRightTag =            "{{SIDE_RIGHT_TAG}}"

    override fun getBaseTempSql(): String? {
        return " $_conditionLogicalTag ($_SideLeftTag) $_conditionOperationTag ($_SideRightTag)"
    }

    override fun toSql(): String? {
        var queryTemp = getBaseTempSql();
        queryTemp =  queryTemp?.replace(_conditionLogicalTag,  if (isAddLogical) {conditionLogical} else {""});
        queryTemp =  queryTemp?.replace(_SideLeftTag, sideLeft);
        queryTemp =  queryTemp?.replace(_conditionOperationTag, conditionOperation);
        queryTemp =  queryTemp?.replace(_SideRightTag, sideRight);
        return queryTemp;
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }


}