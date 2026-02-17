package gog.my_project.data_base.query.api.interfaces.api.option_offset

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.option_offset.IQueryOptionOffsetAst

interface IQueryOptionOffsetApi : IQueryApi {

    var ast: IQueryOptionOffsetAst;

    fun setOptionOffset(optionOffset: Long) : IQueryOptionOffsetApi;

}