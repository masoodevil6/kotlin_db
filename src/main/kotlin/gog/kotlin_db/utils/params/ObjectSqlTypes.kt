package gog.kotlin_db.utils.params

import gog.kotlin_db.data.base.query_template.ITemplateSqlType
import gog.kotlin_db.data.base.query_template.TemplateSqlTypeMySql
import gog.kotlin_db.data.base.query_template.TemplateSqlTypeSqlLite

object ObjectSqlTypes {
    val _TYPE_MY_SQL : ITemplateSqlType =    TemplateSqlTypeMySql();
    val _TYPE_SQL_LITE : ITemplateSqlType =  TemplateSqlTypeSqlLite();

//    const val _TYPE_POSTGRES_SQL : ITemplateSqlType =    "_TYPE_POSTGRES_SQL";
//    const val _TYPE_SQL_SERVER : ITemplateSqlType =      "_TYPE_SQL_LITE";
//    const val _TYPE_ORACLE : ITemplateSqlType =          "_TYPE_ORACLE";

}