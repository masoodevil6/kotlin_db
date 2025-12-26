package gog.my_project.query.query_builder.tools.options

import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionOffset


class QueryToolsOptionOffset(

):
    IQueryToolsOptionOffset
{
    var _pageOffset : Int? = null;

    override fun setOptionOffset(optionOffset: Int) : IQueryToolsOptionOffset {
        this._pageOffset = optionOffset;
        return this;
    }


    override fun getBaseTempSql(): String? {
        return ""
    }

    override fun toSql(): String? {
        return DatabaseConfig.dbTypeName.getOptionOffsetSql(_pageOffset);
    }

    override fun replaceInBaseTemp(query: String): String {
        val queryOption= toSql();
        return query.replace(ObjectSqlTypeTemplates._TAG_TEMP_OPTION_OFFSET, queryOption ?: "");
    }



}