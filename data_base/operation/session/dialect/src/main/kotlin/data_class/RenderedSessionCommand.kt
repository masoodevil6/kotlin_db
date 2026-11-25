package gog.my_project.data_base_session.dialect.data_class

import gog.my_project.data_base_session.ast.enums.SessionCommandKind

data class RenderedSessionCommand(
    val kind: SessionCommandKind,
    val sql: String,
    val parameters: List<SessionCommandParameter>,
)
