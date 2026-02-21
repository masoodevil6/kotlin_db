package gog.my_project.data_base.query.renderer.manager

import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect

class DialectSelector{

    private val dialects: Map<DialectQuery , ISqlDialect> = mapOf(
        DialectQuery.MY_SQL to MySqlDialect(),
    )

    fun select(type: DialectQuery): ISqlDialect {
        return dialects[type] ?: error("dialect not registered: $type")
    }

}