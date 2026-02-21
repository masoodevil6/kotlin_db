package gog.my_project.data_base.query.api.interfaces.api

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.ast.interfaces.IQueryAst

interface IQueryApi<A: IQueryAst> {

    var ast: A;

    var params: MutableList<SqlParameter<*>>

}