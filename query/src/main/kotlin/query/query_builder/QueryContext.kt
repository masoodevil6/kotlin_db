package gog.my_project.query.query_builder

import gog.my_project.core.sql_dialect.DialectQuery
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.sql_dialect.SqlDialectFactory

class QueryContext(
    dialectQuery: DialectQuery
) {

    private val sqlDialect: ISqlDialect =
        SqlDialectFactory().create(dialectQuery)

    fun createQueryBuilder(): QueryBuilder {
        return QueryBuilder(sqlDialect)
    }
}