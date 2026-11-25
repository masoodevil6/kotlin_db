package gog.my_project.data_base_session.api.interfaces.api

import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst

interface ISessionApi<A : ISessionCommandAst> {
    var ast: A
}
