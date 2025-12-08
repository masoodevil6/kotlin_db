package gog.kotlin_db.data.base.QueryBuilder.tools.options

import gog.kotlin_db.data.base.QueryBuilder.tools.options.OptionGroup.IQueryTools_optionGroup
import gog.kotlin_db.data.base.QueryBuilder.tools.options.OptionGroup.QueryTools_optionGroup
import gog.kotlin_db.data.base.QueryBuilder.tools.options.OptionOrder.IQueryTools_optionOrder
import gog.kotlin_db.data.base.QueryBuilder.tools.options.OptionOrder.QueryTools_optionOrder
import gog.kotlin_db.data.base.QueryBuilder.tools.options.OptionPageInit.IQueryTools_optionPageInit
import gog.kotlin_db.data.base.QueryBuilder.tools.options.OptionPageInit.QueryTools_optionPageInit

class QueryTools_options(

) :
    IQueryTools_options
{

    companion object {

        const val _TAG_TEMP_OPTION =                   "{{_TAG_TEMP_OPTION}}"

        const val _TAG_TEMP_OPTION_GROUP =              "{{_TAG_TEMP_OPTION_GROUP}}"
        const val _TAG_TEMP_OPTION_GROUP_COLUMNS =      "{{_TAG_TEMP_OPTION_GROUP_COLUMNS}}"

        const val _TAG_TEMP_OPTION_ORDER =              "{{_TAG_TEMP_OPTION_ORDER}}"
        const val _TAG_TEMP_OPTION_ORDER_COLUMNS =      "{{_TAG_TEMP_OPTION_ORDER_COLUMNS}}"
        const val _TAG_TEMP_OPTION_ORDER_TYPE =         "{{_TAG_TEMP_OPTION_ORDER_TYPE}}"

        const val _TAG_TEMP_OPTION_PAGE_INIT =          "{{_TAG_TEMP_OPTION_PAGE_INIT}}"
        const val _TAG_TEMP_OPTION_PAGE_INIT_LIMIT =    "{{_TAG_TEMP_OPTION_PAGE_INIT_LIMIT}}"
        const val _TAG_TEMP_OPTION_PAGE_INIT_OFFSET =   "{{_TAG_TEMP_OPTION_PAGE_INIT_OFFSET}}"
    }


    var _queryToolsOptionGroup : IQueryTools_optionGroup? = QueryTools_optionGroup();
    var _queryToolsOptionOrder : IQueryTools_optionOrder? = QueryTools_optionOrder();
    var _queryToolsOptionPageInit : IQueryTools_optionPageInit? = QueryTools_optionPageInit();



    override fun optionsSetup(blockGroup: (IQueryTools_options) -> QueryTools_options): QueryTools_options {
        return  blockGroup(QueryTools_options());
    }


    override fun addGroup(columnName: String): QueryTools_options {
        _queryToolsOptionGroup?.addColumnGroup(columnName);
        return this;
    }




    override fun addOrder(columnName: String): QueryTools_options {
        _queryToolsOptionOrder?.addColumnOrder(columnName);
        return this;
    }

    override fun orderType(orderType: String): QueryTools_options {
        _queryToolsOptionOrder?.setTypeOrder(orderType);
        return this;
    }





    override fun pageInit(
        pageLimit: Int,
        pageOffset: Int
    ): QueryTools_options {
        _queryToolsOptionPageInit?.setOptionPageInit_limit(pageLimit);
        _queryToolsOptionPageInit?.setOptionPageInit_offset(pageOffset);
        return this;
    }

    override fun pageLimit(pageLimit: Int): QueryTools_options {
        _queryToolsOptionPageInit?.setOptionPageInit_limit(pageLimit);
        return this;
    }

    override fun pageOffset(pageOffset: Int): QueryTools_options {
        _queryToolsOptionPageInit?.setOptionPageInit_offset(pageOffset);
        return this;
    }















    override fun getBaseTempSql(): String? {
        return " ${_TAG_TEMP_OPTION_GROUP} ${_TAG_TEMP_OPTION_ORDER} ${_TAG_TEMP_OPTION_PAGE_INIT}"
    }

    override fun toSql(): String? {
        var tempSql = getBaseTempSql();
        tempSql = _queryToolsOptionGroup?.replaceInBaseTemp(tempSql!!);
        tempSql = _queryToolsOptionOrder?.replaceInBaseTemp(tempSql!!);
        tempSql = _queryToolsOptionPageInit?.replaceInBaseTemp(tempSql!!);
        return tempSql;
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryOption= toSql();
        return query.replace(QueryTools_options._TAG_TEMP_OPTION, queryOption ?: "");
    }

}