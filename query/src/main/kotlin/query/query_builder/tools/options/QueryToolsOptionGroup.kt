package gog.my_project.query.query_builder.tools.options

import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase
import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionGroup
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.tools.column.QueryToolsColumnsBase
import gog.my_project.tools.templates.OTemplateSqlDialect


class QueryToolsOptionGroup(
    private val sqlDialect: ISqlDialect
):
    IQueryToolsOptionGroup
{

    protected var _groupByList: MutableList<String> = mutableListOf();




    override fun addColumn(columnName : String): IQueryToolsOptionGroup {
        _groupByList.add(columnName);
        return this;
    }

    override fun addColumn(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsOptionGroup {
        val builder = QueryToolsColumnsBase(sqlDialect);
        val query = builder.blockColumn().toSql();
        if (query != null){
            return addColumn(query);
        }
        return this;
    }


    override fun toSql(): String? {
        return sqlDialect.getOptionGroupSql(_groupByList);
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryOption= toSql();
        return query.replace(OTemplateSqlDialect._TAG_TEMP_OPTION_GROUP, queryOption ?: "");
    }



}