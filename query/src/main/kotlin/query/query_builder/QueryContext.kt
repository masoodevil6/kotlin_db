package gog.my_project.query.query_builder

import gog.my_project.core.sql_dialect.DialectQuery
import gog.my_project.datas.BuiltQuery
import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.sql_dialect.SqlDialectFactory
import javax.management.Query

class QueryContext(
    dialectQuery: DialectQuery // ISqlDialect
) {


    private var queryBuilder: IQueryBuilder = QueryBuilder();
    private var builtQuery: BuiltQuery? = null;

    private val sqlDialect: ISqlDialect =
        SqlDialectFactory().create(dialectQuery)

    fun setQuery(query: IQueryBuilder) : QueryContext{
        queryBuilder = query;
        return this;
    }

    fun execute() : QueryContext{
        this.builtQuery = queryBuilder.readyExecuteSql(sqlDialect);

        print(this.builtQuery?.params?.size);
        this.builtQuery?.params?.forEach { println(it) }
        return this;
    }

    /*fun createQueryBuilder(): IQueryBuilder {
        return QueryBuilder()
    }*/
}