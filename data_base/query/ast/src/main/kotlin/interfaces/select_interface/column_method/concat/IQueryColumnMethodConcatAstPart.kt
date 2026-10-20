package gog.my_project.data_base.query.ast.interfaces.select_interface.column_method

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst


sealed interface IQueryColumnMethodConcatAstPart {

    data class Column(
        val ast: IQueryColumnsBaseAst
    ) : IQueryColumnMethodConcatAstPart

    data class Text(
        val value: String
    ) : IQueryColumnMethodConcatAstPart
}