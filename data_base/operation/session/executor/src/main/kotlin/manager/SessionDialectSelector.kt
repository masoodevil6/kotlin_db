package gog.my_project.data_base_session.executor.manager

import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_session.dialect.interfaces.ISessionDialect
import gog.my_project.data_base_session.renderer.dialects.MariaDbSessionDialect
import gog.my_project.data_base_session.renderer.dialects.MySqlSessionDialect

internal class SessionDialectSelector {
    private val dialects: Map<DatabaseDialect, ISessionDialect> = mapOf(
        DatabaseDialect.MYSQL to MySqlSessionDialect(),
        DatabaseDialect.MARIA_DB to MariaDbSessionDialect(),
    )

    fun select(type: DatabaseDialect): ISessionDialect =
        dialects[type] ?: error("Session dialect not registered: $type")
}
