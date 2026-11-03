package gog.my_project.data_base_query.ast.interfaces

import gog.my_project.data_base_query.ast.metadata.interfaces.IQueryAstMetadata

interface IQueryAst<MD : IQueryAstMetadata> {
    var metadata: MD
}