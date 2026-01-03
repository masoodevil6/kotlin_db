package gog.my_project.query.query_builder.tools.select


import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumns
import gog.my_project.query.interfaces.query_builders.tools.select.IQueryToolsSelect
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.tools.column.QueryToolsColumns
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

class QueryToolsSelect(
    override var params: MutableList<Any?> = mutableListOf<Any?>()
) : IQueryToolsSelect {


    protected var columns: MutableList<IQueryToolsColumns> = mutableListOf()



    /* ==============================================================
    template
    ============================================================== */
    override fun getListColumns(): MutableList<IQueryToolsColumns> {
        return columns;
    }



    /* ==============================================================
    Builder
    ============================================================== */
    override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getSelectSql(this);
    }





    /* ==============================================================
    structure
    ============================================================== */
    override fun addColumn(blockColumn: IQueryToolsColumns.() -> IQueryToolsColumns): IQueryToolsSelect {
        val builder = QueryToolsColumns();
        columns.add( builder.blockColumn());
        return this;
    }





}