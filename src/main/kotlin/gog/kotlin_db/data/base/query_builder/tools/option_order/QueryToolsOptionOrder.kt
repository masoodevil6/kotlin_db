package gog.kotlin_db.data.base.query_builder.tools.option_order

import gog.kotlin_db.Configs.DatabaseConfig
import gog.kotlin_db.utils.params.ObjectSqlTypeTemplates

class QueryToolsOptionOrder(

):
    IQueryToolsOptionOrder
{

    protected var _orderByList: MutableList<String> = mutableListOf();
    private var _orderType : String? = " asc ";







    override fun orderSetup(blockOrder: (IQueryToolsOptionOrder) -> QueryToolsOptionOrder): QueryToolsOptionOrder {
        return  blockOrder(QueryToolsOptionOrder());
    }


    override fun addColumn(columnName: String): QueryToolsOptionOrder {
        this._orderByList.add(columnName);
        return this;
    }

    override fun type(orderType: String): QueryToolsOptionOrder {
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