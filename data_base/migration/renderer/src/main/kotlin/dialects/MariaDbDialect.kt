package gog.my_project.data_base.migration.renderer.dialects

import gog.my_project.data_base.core.data_base.ProductDatabaseVersion
import gog.my_project.data_base.core.data_base.DatabaseProduct

/** MariaDB currently shares the registered migration grammar with MySQL. */
class MariaDbDialect(targetVersion: ProductDatabaseVersion? = null) :
    MySqlDialect(targetVersion, DatabaseProduct.MARIA_DB)
