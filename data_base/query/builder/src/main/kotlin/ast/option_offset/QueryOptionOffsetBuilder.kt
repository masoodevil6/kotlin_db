package gog.my_project.data_base.query.builder.ast.option_offset

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.option_offset.IQueryOptionOffsetApi
import gog.my_project.data_base.query.ast.interfaces.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.ast.schema.option_offset.QueryOptionOffsetAst

class QueryOptionOffsetBuilder(
    override var ast: IQueryOptionOffsetAst = QueryOptionOffsetAst(),
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
): IQueryOptionOffsetApi {

    override fun setOptionOffset(
        optionOffset: Long
    ): IQueryOptionOffsetApi
    {
        this.ast.pageOffset = optionOffset;
        return this;
    }

}