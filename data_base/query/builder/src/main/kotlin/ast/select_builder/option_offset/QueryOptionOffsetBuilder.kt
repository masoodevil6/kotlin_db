package gog.my_project.data_base.query.builder.ast.select_builder.option_offset

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_offset.IQueryOptionOffsetApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_offset.QueryOptionOffsetAst

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