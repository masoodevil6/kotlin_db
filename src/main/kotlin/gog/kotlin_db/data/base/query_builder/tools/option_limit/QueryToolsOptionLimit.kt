package gog.kotlin_db.data.base.query_builder.tools.option_limit

import gog.kotlin_db.Configs.DatabaseConfig
import gog.kotlin_db.utils.params.ObjectSqlTypeTemplates

class QueryToolsOptionLimit(

):
    IQueryToolsOptionLimit
{

    var _pageLimit : Int? = null;

    override fun setOptionLimit(optionLimit: Int) : QueryToolsOptionLimit{
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