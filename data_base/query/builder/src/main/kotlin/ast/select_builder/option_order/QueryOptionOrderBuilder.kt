package gog.my_project.data_base.query.builder.ast.select_builder.option_order

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_order.IQueryOptionOrderApi
import gog.my_project.data_base.query.api.tools.enums.SqlOrderType
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_order.IQueryOptionOrderAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_order.QueryOptionOrderAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.select_builder.column_base.QueryColumnsBaseBuilder

class QueryOptionOrderBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryOptionOrderAst = QueryOptionOrderAst(),
) : IQueryOptionOrderApi {


    /* ==============================================================
    structure [Order Type]
    ============================================================== */
    override fun orderType(type: SqlOrderType): IQueryOptionOrderApi {
        this.ast.orderType = type.value;
        return this;
    }

    override fun orderAsc(): IQueryOptionOrderApi {
        return this.orderType(SqlOrderType.Asc)
    }

    override fun orderDesc(): IQueryOptionOrderApi {
        return this.orderType(SqlOrderType.Asc)
    }



    /* ==============================================================
    structure [Order Column]
    ============================================================== */
    override fun addColumn(
        blockColumn: IQueryColumnsBaseApi.() -> Unit
    ): IQueryOptionOrderApi
    {
        val ast = QueryColumnsBaseAst();
        QueryColumnsBaseBuilder(
            params,
            ast
        ).apply(blockColumn);
        this.ast.orderByList.add(ast);
        return this;
    }

}