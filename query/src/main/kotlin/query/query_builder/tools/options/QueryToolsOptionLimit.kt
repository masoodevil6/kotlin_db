package gog.my_project.query.query_builder.tools.options

import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionLimit
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.tools.templates.OTemplateSqlDialect


class QueryToolsOptionLimit(
    private val sqlDialect: ISqlDialect
):
    IQueryToolsOptionLimit
{

    var _pageLimit : Int? = null;

    override fun setOptionLimit(optionLimit: Int) : IQueryToolsOptionLimit {
        this._pageLimit = optionLimit;
        return this;
    }




    override fun toSql(): String? {
        return sqlDialect.getOptionLimitSql(_pageLimit);
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryOption= toSql();
        return query.replace(OTemplateSqlDialect._TAG_TEMP_OPTION_LIMIT, queryOption ?: "");
    }




}