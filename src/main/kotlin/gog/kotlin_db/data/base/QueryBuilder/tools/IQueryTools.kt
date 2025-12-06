package gog.kotlin_db.data.base.QueryBuilder.tools

interface IQueryTools {

    public fun getBaseTempSql(): String?;
    public fun toSql(): String?;
    public fun replaceInBaseTemp(query: String): String;

}