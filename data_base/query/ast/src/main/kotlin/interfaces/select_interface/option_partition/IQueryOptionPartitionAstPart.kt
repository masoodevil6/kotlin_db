package gog.my_project.data_base.query.ast.interfaces.select_interface.option_partition

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst

sealed interface IQueryOptionPartitionAstPart {

    data class Column(
        val ast: IQueryColumnsBaseAst
    ) : IQueryOptionPartitionAstPart

}