package gog.my_project.data_base.query.builder.ast.select_builder.column

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.column.IQueryColumnsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.tools.enums.SqlMethodColumn
import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.schema.select_schema.column.QueryColumnsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.select_builder.column_base.QueryColumnsBaseBuilder

class QueryColumnsBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryColumnsAst = QueryColumnsAst(),
)  : IQueryColumnsApi {


    /* ==============================================================
    structure [method]
    ============================================================== */

    override fun method(method: SqlMethodColumn): IQueryColumnsApi {
        this.ast.ColumnMethod = method.value;
        return this;
    }

    override fun sum(): IQueryColumnsApi {
        return this.method(SqlMethodColumn.Sum);
    }

    override fun count(): IQueryColumnsApi {
        return this.method(SqlMethodColumn.Count);
    }

    override fun avg(): IQueryColumnsApi {
        return this.method(SqlMethodColumn.Avg);
    }

    override fun min(): IQueryColumnsApi {
        return this.method(SqlMethodColumn.Min);
    }

    override fun max(): IQueryColumnsApi {
        return this.method(SqlMethodColumn.Max);
    }



    /* ==============================================================
    structure [column]
    ============================================================== */

    override fun column(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnsApi {
        var ast = QueryColumnsBaseAst();
        QueryColumnsBaseBuilder(
            params,
            ast
        ).apply(blockColumn);
        this.ast.Column = ast;
        return this;
    }



    /* ==============================================================
    structure [alias]
    ============================================================== */
    override fun alias(alias: String): IQueryColumnsApi {
        this.ast.ColumnAlias = alias;
        return this;
    }


}