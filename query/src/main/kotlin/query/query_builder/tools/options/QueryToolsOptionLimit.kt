package gog.my_project.query.query_builder.tools.options

import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionLimit


class QueryToolsOptionLimit(

):
    IQueryToolsOptionLimit
{

    var _pageLimit : Int? = null;

    override fun setOptionLimit(optionLimit: Int) : IQueryToolsOptionLimit {
        this._pageLimit = optionLimit;
        return this;
    }


    override fun getBaseTempSql(): String? {
        return ""
    }

    override fun toSql(): String? {
        return DatabaseConfig.dbTypeName.getOptionLimitSql(_pageLimit);
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryOption= toSql();
        return query.replace(ObjectSqlTypeTemplates._TAG_TEMP_OPTION_LIMIT, queryOption ?: "");
    }




}