package gog.my_project.data_base.query.renderer.manager

import gog.my_project.data_base.builder.base.DatabaseBuilder
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.interfaces.IQueryBuilder
import gog.my_project.data_base.query.dialect.manager.SqlDialectFactory
import gog.my_project.data_base.query.renderer.tools.ExecuteResult
import java.sql.SQLException

class QueryManager(

) {

    fun connectToDataBase() : DatabaseBuilder {
        return DatabaseBuilder()
            .config {
                domain("jdbc:mysql://127.0.0.1")
                port(3306)
                name("kotlin_db")
                username("root")
                password("")
            }
            .dialect(DialectQuery.MY_SQL);
    }


    fun execute(
        queryBuilder: IQueryBuilder,
        blockExecute: (ExecuteResult) -> Unit,
        blockQueryInfo: ((query: String , paramsMap: MutableMap<String , Any?>) -> Unit)? = null
    ){
        val db = this.connectToDataBase()

        val query: String? = SqlDialectFactory(db.getDialect()).translate(queryBuilder);
        val params: MutableList<SqlParameter<*>> = queryBuilder.params;

        if (query != null){

            if (blockQueryInfo != null){
                val paramsMap: MutableMap<String , Any?> = params.associate { it -> it.name to it.value }.toMutableMap()

                blockQueryInfo(query , paramsMap)
            }


            try {
                db.execute(
                    builtQuery = BuiltQuery(query, params),
                    blockExecute = {
                        result ->
                        blockExecute(
                            ExecuteResult.SuccessExecute(result)
                        )
                    }
                )

            }
            catch (ex: SQLException){
                blockExecute(
                    ExecuteResult.ErrorExecute(ex)
                )
            }
        }
        else{
            blockExecute(
                ExecuteResult.Error("Error executing query ")
            )
        }

    }

}