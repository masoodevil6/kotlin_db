package gog.my_project.data_base.query.builder.ast.option_offset

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.option_offset.IQueryOptionOffsetApi
import gog.my_project.data_base.query.ast.interfaces.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.ast.schema.option_offset.QueryOptionOffsetAst

class QueryOptionOffsetBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryOptionOffsetAst = QueryOptionOffsetAst(),
): IQueryOptionOffsetApi {

    override fun setOptionOffset(
        optionOffset: Long
    ): IQueryOptionOffsetApi
    {
        this.ast.pageOffset = optionOffset;
        return this;
    }

}