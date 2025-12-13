package gog.kotlin_db.data.base.query_builder.tools.option_offset

import gog.kotlin_db.Configs.DatabaseConfig
import gog.kotlin_db.utils.params.ObjectSqlTypeTemplates

class QueryToolsOptionOffset(

):
    IQueryToolsOptionOffset
{
    var _pageOffset : Int? = null;

    override fun setOptionOffset(optionOffset: Int): QueryToolsOptionOffset {
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