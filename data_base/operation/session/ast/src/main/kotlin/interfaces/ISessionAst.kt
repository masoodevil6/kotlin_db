package gog.my_project.data_base_session.ast.interfaces

import gog.my_project.data_base_session.ast.metadata.interfaces.ISessionAstMetadata

interface ISessionAst<MD : ISessionAstMetadata> {
    var metadata: MD
}
