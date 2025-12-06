package gog.kotlin_db.data.base.QueryBuilder.tools.options.OptionPageInit

import gog.kotlin_db.data.base.QueryBuilder.tools.options.QueryTools_options

class QueryTools_optionPageInit(

):
    IQueryTools_optionPageInit
{

    private var _pageLimit : Int? = 0;
    private var _pageOffset : Int? = 0;


    override fun setOptionPageInit_limit(pageLimit: Int) {
        this._pageLimit = pageLimit;
    }

    override fun setOptionPageInit_offset(pageOffset: Int) {
        this._pageOffset = pageOffset;
    }


    override fun getBaseTempSql(): String? {
        return " limit ${QueryTools_options._TAG_TEMP_OPTION_PAGE_INIT_LIMIT} offset ${QueryTools_options._TAG_TEMP_OPTION_PAGE_INIT_OFFSET}"
    }

    override fun toSql(): String? {
        if (_pageLimit!! > 0 && _pageOffset!! > 0){
            var queryTemp = getBaseTempSql();
            queryTemp = queryTemp?.replace(QueryTools_options._TAG_TEMP_OPTION_PAGE_INIT_LIMIT, _pageLimit.toString() ?: 0.toString());
            queryTemp = queryTemp?.replace(QueryTools_options._TAG_TEMP_OPTION_PAGE_INIT_OFFSET, _pageOffset.toString() ?: 0.toString());
            return queryTemp;
        }
        return null;
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryOption= toSql();
        return query.replace(QueryTools_options._TAG_TEMP_OPTION_PAGE_INIT, queryOption ?: "");
    }


}