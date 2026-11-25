package gog.my_project.data_base_session.ast.interfaces

import gog.my_project.data_base_session.ast.enums.SessionCommandKind

interface ISessionCommandAst {
    val kind: SessionCommandKind
}
