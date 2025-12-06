package gog.kotlin_db.data.base.QueryBuilder

interface IQueryBuilder {

    public fun getBaseTempSql(): String?;
    public fun toSql(): String?;
    public fun replaceInBaseTemp(query: String): String;

}