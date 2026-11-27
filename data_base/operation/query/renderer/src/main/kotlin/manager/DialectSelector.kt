package gog.my_project.data_base_query.renderer.manager

import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base_query.renderer.dialects.MySqlDialect
import gog.my_project.data_base_query.renderer.dialects.MariaDbDialect

class DialectSelector{

    private val dialects: Map<DatabaseDialect , ISqlDialect> = mapOf(
        DatabaseDialect.MYSQL to MySqlDialect(),
        DatabaseDialect.MARIA_DB to MariaDbDialect(),
    )

    fun select(type: DatabaseDialect): ISqlDialect {
        return dialects[type] ?: error("dialect not registered: $type")
    }

}
