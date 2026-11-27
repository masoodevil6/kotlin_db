package gog.my_project.data_base_session.renderer.dialects

import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_session.dialect.manager.BaseSessionDialect

class MariaDbSessionDialect : BaseSessionDialect(
    dialect = DatabaseDialect.MARIA_DB,
    registerRenders = { registerMySqlCompatibleAdvisoryLockRenderers() },
)
