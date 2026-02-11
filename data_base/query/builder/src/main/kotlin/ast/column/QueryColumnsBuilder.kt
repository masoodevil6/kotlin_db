package gog.my_project.data_base.query.builder.ast.column

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.column.IQueryColumnsApi
import gog.my_project.data_base.query.api.interfaces.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.tools.enums.SqlMethodColumn
import gog.my_project.data_base.query.ast.interfaces.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.schema.column.QueryColumnsAst
import gog.my_project.data_base.query.ast.schema.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.column_base.QueryColumnsBaseBuilder

class QueryColumnsBuilder(
    override var ast: IQueryColumnsAst = QueryColumnsAst(),
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
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
        QueryColumnsBaseBuilder(ast, params).apply(blockColumn);
        this.ast.ColumnName = ast;
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