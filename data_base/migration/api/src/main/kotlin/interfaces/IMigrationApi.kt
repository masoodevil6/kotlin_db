package gog.my_project.data_base.migration.api.interfaces

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst

interface IMigrationApi<A: IMigrationAst> {

    var ast: A;

    var params: MutableList<SqlParameter<*>>
}