package gog.my_project.data_base_query.api.interfaces.api

import gog.my_project.data_base_query.ast.interfaces.IQueryAst

interface IQueryApi<A: IQueryAst<*>> {

    var ast: A;

}