package gog.my_project.query.sql_dialect

import gog.my_project.core.sql_dialect.DialectQuery
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect

class SqlDialectFactory {
    fun create(dialect: DialectQuery): ISqlDialect =
        when (dialect) {
            DialectQuery.MY_SQL -> MySqlDialect()
           // DialectQuery.SQL_LITE ->  SqlLiteDialect()
        }
}