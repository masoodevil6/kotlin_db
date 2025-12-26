package gog.my_project.query.query_builder.tools.conditions

import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditions


class QueryToolsConditions(
    val conditionLogical: String,
    val sideLeft: String? ,
    val conditionOperation: String ,
    val sideRight : String? ,
):
    IQueryToolsConditions
{



    public var isAddLogical: Boolean = false;




    override fun toWhereSql(isAddLogical: Boolean): String? {
        this.isAddLogical = isAddLogical;
        return toSql();
    }




    override fun getBaseTempSql(): String? {
        return "";
    }

    override fun toSql(): String? {
        return DatabaseConfig.dbTypeName.getConditionSql(
            conditionLogical ,
            sideLeft ,
            conditionOperation ,
            sideRight ,
            isAddLogical
        );
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }


}