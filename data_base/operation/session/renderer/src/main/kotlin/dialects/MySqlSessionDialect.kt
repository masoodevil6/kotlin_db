package gog.my_project.data_base_session.renderer.dialects

import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_session.dialect.manager.BaseSessionDialect

class MySqlSessionDialect : BaseSessionDialect(
    dialect = DatabaseDialect.MYSQL,
    registerRenders = { registerMySqlCompatibleAdvisoryLockRenderers() },
)
