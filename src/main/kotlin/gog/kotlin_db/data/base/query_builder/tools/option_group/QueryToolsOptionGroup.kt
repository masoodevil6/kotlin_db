package gog.kotlin_db.data.base.query_builder.tools.option_group

import gog.kotlin_db.Configs.DatabaseConfig
import gog.kotlin_db.utils.params.ObjectSqlTypeTemplates

class QueryToolsOptionGroup(

):
    IQueryToolsOptionGroup
{

    protected var _groupByList: MutableList<String> = mutableListOf();



    override fun groupSetup(blockGroup: (IQueryToolsOptionGroup) -> QueryToolsOptionGroup): QueryToolsOptionGroup {
        return  blockGroup(QueryToolsOptionGroup());
    }

    override fun addColumn(columnName: String): QueryToolsOptionGroup {
        _groupByList.add(columnName);
        return this;
    }








    override fun getBaseTempSql(): String? {
        return ""
    }

    override fun toSql(): String? {
        return DatabaseConfig.dbTypeName.getOptionGroupSql(_groupByList);
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryOption= toSql();
        return query.replace(ObjectSqlTypeTemplates._TAG_TEMP_OPTION_GROUP, queryOption ?: "");
    }



}