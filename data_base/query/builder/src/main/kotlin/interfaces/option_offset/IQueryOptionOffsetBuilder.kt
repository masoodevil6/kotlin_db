package gog.my_project.data_base.query.builder.interfaces.option_offset

import gog.my_project.data_base.query.ast.interfaces.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.builder.interfaces.IQuery

interface IQueryOptionOffsetBuilder : IQuery {

    var ast: IQueryOptionOffsetAst;

    fun setOptionOffset(optionOffset: Long) : IQueryOptionOffsetBuilder;

}