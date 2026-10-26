package gog.my_project.data_base.query.ast.metadata.delete.query_render_delete

import gog.my_project.data_base.query.ast.metadata.interfaces.IQueryAstMetadata

class QueryRenderDeleteAstMetadata : IQueryAstMetadata {
    var targets: MutableList<String> = mutableListOf()
}
