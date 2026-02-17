package gog.my_project.data_base.query.builder.interfaces.builder.option_offset

import gog.my_project.data_base.query.ast.interfaces.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.builder.interfaces.builder.IQuery

interface IQueryOptionOffsetBuilder : IQuery {

    var ast: IQueryOptionOffsetAst;

    fun setOptionOffset(optionOffset: Long) : IQueryOptionOffsetBuilder;

}