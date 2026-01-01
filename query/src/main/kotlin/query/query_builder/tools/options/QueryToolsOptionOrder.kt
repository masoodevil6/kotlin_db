package gog.my_project.query.query_builder.tools.options

import gog.my_project.enums.SqlOrderType
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase
import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionOrder
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.tools.column.QueryToolsColumnsBase
import gog.my_project.tools.templates.OTemplateSqlDialect


class QueryToolsOptionOrder(
    private val sqlDialect: ISqlDialect
):
    IQueryToolsOptionOrder
{

    protected var _orderByList: MutableList<String> = mutableListOf();
    private var _orderType : String? = SqlOrderType.Asc.value;





    override fun type(orderType : SqlOrderType) : IQueryToolsOptionOrder {
        this._orderType= orderType.value;
        return this;
    }

    override fun typeAsc(): IQueryToolsOptionOrder {
        return this.type(SqlOrderType.Asc)
    }

    override fun typeDesc(): IQueryToolsOptionOrder {
        return this.type(SqlOrderType.Desc)
    }


    override fun addColumn(columnName : String) : IQueryToolsOptionOrder {
        this._orderByList.add(columnName);
        return this;
    }

    override fun addColumn(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsOptionOrder {
        val builder = QueryToolsColumnsBase(sqlDialect);
        val query = builder.blockColumn().toSql();
        if (query != null){
            return addColumn(query);
        }
        return this;
    }












    override fun toSql(): String? {
        return sqlDialect.getOptionOderSql(_orderByList , _orderType);
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryOption= toSql();
        return query.replace(OTemplateSqlDialect._TAG_TEMP_OPTION_ORDER, queryOption ?: "");
    }



}