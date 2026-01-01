package gog.my_project.query.query_builder.tools.select


import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumns
import gog.my_project.query.interfaces.query_builders.tools.select.IQueryToolsSelect
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.tools.column.QueryToolsColumns
import gog.my_project.tools.templates.OTemplateSqlDialect
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

class QueryToolsSelect(
    private val sqlDialect: ISqlDialect
) : IQueryToolsSelect {


    protected var columns: MutableList<IQueryToolsColumns> = mutableListOf()

    override fun getListColumns(): MutableList<IQueryToolsColumns> {
        return columns;
    }










    override fun addColumn(blockColumn: IQueryToolsColumns.() -> IQueryToolsColumns): IQueryToolsSelect {
        val builder = QueryToolsColumns(sqlDialect);
        columns.add( builder.blockColumn());
        return this;
    }






    override fun toSql(): String? {
        return sqlDialect.getSelectSql(this);
    }

    override fun replaceInBaseTemp(query: String): String {
        val querySelect= toSql();
        return query.replace(OTemplateSqlDialect._TAG_TEMP_SELECT, querySelect ?: "");
    }


}