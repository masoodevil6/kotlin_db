package gog.my_project.data_base_session.renderer.dialects

import gog.my_project.data_base_session.dialect.enums.SessionDialect
import gog.my_project.data_base_session.dialect.manager.BaseSessionDialect

class MariaDbSessionDialect : BaseSessionDialect(
    dialect = SessionDialect.MARIA_DB,
    registerRenders = { registerMySqlCompatibleAdvisoryLockRenderers() },
)
