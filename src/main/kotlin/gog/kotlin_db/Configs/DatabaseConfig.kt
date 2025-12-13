package gog.kotlin_db.Configs

import gog.kotlin_db.data.base.query_template.ITemplateSqlType
import gog.kotlin_db.utils.params.ObjectSqlTypes

object DatabaseConfig{

    // val dbDomain    : String = "jdbc:mysql://localhost",
    val dbDomain    : String = "jdbc:mysql://127.0.0.1";
    val dbPort      : Int    = 3306;
    val dbName      : String = "kotlin_db";
    val dbUserName  : String = "root";
    val dbPassword  : String = "" ;
    val dbPoolSize  : Int    = 10 ;

    val dbTypeName : ITemplateSqlType = ObjectSqlTypes._TYPE_MY_SQL ;

}