package gog.kotlin_db.data.base.query_builder.tools.conditions.QueryConditions

import gog.kotlin_db.Configs.DatabaseConfig
import gog.kotlin_db.utils.params.ObjectSqlTypeTemplates

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