package gog.my_project.query.query_builder.tools.options

import gog.my_project.datas.SqlParameter
import gog.my_project.enums.SqlOrderType
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase
import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionOrder
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.tools.column.QueryToolsColumnsBase

class QueryToolsOptionOrder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
):
    IQueryToolsOptionOrder
{

    protected var _orderByList: MutableList<IQueryToolsColumnsBase> = mutableListOf();
    private var _orderType : SqlOrderType = SqlOrderType.Asc;


    /* ==============================================================
    template
    ============================================================== */
    override fun getOrderType(): SqlOrderType {
        return _orderType;
    }

    override fun getListColumns(): MutableList<IQueryToolsColumnsBase> {
        return _orderByList;
    }






    /* ==============================================================
    Builder
    ============================================================== */
    override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getOptionOderSql(this);
    }








    /* ==============================================================
    structure
    ============================================================== */
    override fun orderAsc(): IQueryToolsOptionOrder {
        this._orderType= SqlOrderType.Asc;
        return this;
    }

    override fun orderDesc(): IQueryToolsOptionOrder {
        this._orderType= SqlOrderType.Desc;
        return this;
    }








    override fun addColumn(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsOptionOrder {
        val builder = QueryToolsColumnsBase(params);
        val column = builder.blockColumn();
        _orderByList.add(column);
        return this;
    }











}