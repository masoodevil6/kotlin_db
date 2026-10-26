package gog.my_project.data_base.query.meta_data.ast

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.metadata.interfaces.IQueryAstMetadata

/** Selects explicitly requested, typed relationships from AST nodes. */
class AstSelector<T : IQueryAst<*>> private constructor(
    val nodes: List<T>,
) {
    val metadata: List<IQueryAstMetadata>
        get() = nodes.map { it.metadata }

    fun <N : IQueryAst<*>> select(relationship: (T) -> N?): AstSelector<N> =
        AstSelector(nodes.mapNotNull(relationship))

    fun <N : IQueryAst<*>> selectMany(relationship: (T) -> Iterable<N>): AstSelector<N> =
        AstSelector(nodes.flatMap(relationship))

    companion object {
        fun <T : IQueryAst<*>> from(root: T): AstSelector<T> =
            AstSelector(listOf(root))
    }
}
