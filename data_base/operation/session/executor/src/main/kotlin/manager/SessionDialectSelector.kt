package gog.my_project.data_base_session.executor.manager

import gog.my_project.data_base_session.dialect.enums.SessionDialect
import gog.my_project.data_base_session.dialect.interfaces.ISessionDialect
import gog.my_project.data_base_session.renderer.dialects.MariaDbSessionDialect
import gog.my_project.data_base_session.renderer.dialects.MySqlSessionDialect

internal class SessionDialectSelector {
    private val dialects: Map<SessionDialect, ISessionDialect> = mapOf(
        SessionDialect.MYSQL to MySqlSessionDialect(),
        SessionDialect.MARIA_DB to MariaDbSessionDialect(),
    )

    fun select(type: SessionDialect): ISessionDialect =
        dialects[type] ?: error("Session dialect not registered: $type")
}
