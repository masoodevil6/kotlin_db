package gog.my_project.query.query_builder.tools.with.collections

import gog.my_project.query.interfaces.query_builders.tools.with.collections.IQueryToolsWithsCollection
import gog.my_project.query.interfaces.query_builders.tools.with.item.IQueryToolsWithItem
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.tools.with.item.QueryToolsWithItem
import gog.my_project.tools.templates.OTemplateSqlDialect

class QueryToolsWithsCollection(
    private val sqlDialect: ISqlDialect
) :
    IQueryToolsWithsCollection
{


    protected var withs: MutableList<IQueryToolsWithItem> = mutableListOf()
    override fun getListWiths(): MutableList<IQueryToolsWithItem> {
        return withs;
    }



    override fun addWith(blockWith: IQueryToolsWithItem.() -> IQueryToolsWithItem): IQueryToolsWithsCollection {
        val builder = QueryToolsWithItem(sqlDialect);
        withs.add(builder.blockWith());
        return this;
    }




    override fun toSql(): String? {
        return sqlDialect.getWithSql(this);
    }

    override fun replaceInBaseTemp(query: String): String {
        val querySelect= toSql();
        return query.replace(OTemplateSqlDialect._TAG_TEMP_WITH, querySelect ?: "");
    }



}