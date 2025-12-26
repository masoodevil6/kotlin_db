package gog.my_project.query.query_builder.tools.options

import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionOrder


class QueryToolsOptionOrder(

):
    IQueryToolsOptionOrder
{

    protected var _orderByList: MutableList<String> = mutableListOf();
    private var _orderType : String? = " asc ";







    override fun orderSetup(blockOrder: (IQueryToolsOptionOrder) -> IQueryToolsOptionOrder) : IQueryToolsOptionOrder {
        return  blockOrder(QueryToolsOptionOrder());
    }


    override fun addColumn(columnName : String) : IQueryToolsOptionOrder {
        this._orderByList.add(columnName);
        return this;
    }

    override fun type(orderType : String) : IQueryToolsOptionOrder {
        this._orderType= orderType;
        return this;
    }










    override fun getBaseTempSql(): String? {
        return ""
    }

    override fun toSql(): String? {
        return DatabaseConfig.dbTypeName.getOptionOderSql(_orderByList , _orderType);
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryOption= toSql();
        return query.replace(ObjectSqlTypeTemplates._TAG_TEMP_OPTION_ORDER, queryOption ?: "");
    }



}