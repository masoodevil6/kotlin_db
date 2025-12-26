package gog.my_project.query.query_builder.tools.options

import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionGroup


class QueryToolsOptionGroup(

):
    IQueryToolsOptionGroup
{

    protected var _groupByList: MutableList<String> = mutableListOf();



    override fun groupSetup(blockGroup: (IQueryToolsOptionGroup) -> IQueryToolsOptionGroup) : IQueryToolsOptionGroup{
        return  blockGroup(QueryToolsOptionGroup());
    }

    override fun addColumn(columnName : String): IQueryToolsOptionGroup {
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