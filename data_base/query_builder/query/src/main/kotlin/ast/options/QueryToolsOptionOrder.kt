package gog.my_project.data_base.query_builder.query.ast.options

import gog.my_project.data_base.connection.tools.datas.SqlParameter
import gog.my_project.data_base.query_builder.query.ast.column.QueryToolsColumnsBase
import gog.my_project.data_base.query_builder.query.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionOrder
import gog.my_project.data_base.query_builder.query.tools.enums.SqlOrderType


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
    /*override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getOptionOderSql(this);
    }*/








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