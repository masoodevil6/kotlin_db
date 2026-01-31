package gog.my_project.data_base.query.builder.ast.options

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.ast.column.QueryToolsColumnsBase
import gog.my_project.data_base.query.builder.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionOrder
import gog.my_project.data_base.query.definition.interfaces.column.IQueryDefinitionColumnsBase
import gog.my_project.data_base.query.definition.interfaces.options.IQueryDefinitionOptionOrder
import gog.my_project.data_base.query.definition.tools.enums.SqlOrderType

class QueryToolsOptionOrder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
):
    IQueryToolsOptionOrder , IQueryDefinitionOptionOrder
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
    structure
    ============================================================== */
    override fun orderAsc(): IQueryDefinitionOptionOrder {
        this._orderType= SqlOrderType.Asc;
        return this;
    }

    override fun orderDesc(): IQueryDefinitionOptionOrder {
        this._orderType= SqlOrderType.Desc;
        return this;
    }








    override fun addColumn(blockColumn: IQueryDefinitionColumnsBase.() -> IQueryDefinitionColumnsBase): IQueryDefinitionOptionOrder {
        val builder = QueryToolsColumnsBase(params);
        val column = builder.blockColumn();
        _orderByList.add(column as IQueryToolsColumnsBase);
        return this;
    }


}