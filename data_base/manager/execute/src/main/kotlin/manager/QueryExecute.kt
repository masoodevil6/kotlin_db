package gog.my_project.data_base.manager.execute.manager

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import kotlin.use

class QueryExecute(

) : IQueryExecute {



    override fun executeSelect(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<ResultSet>) -> Unit
    ) {
        this.execute(
            builtQuery = builtQuery ,
            blockExecute =  {
                conn , query , params , paramsNames , error ->
                if (error != null) {
                    blockExecute(ExecuteResult.Failure(error))
                    return@execute
                }
                else{
                    conn.prepareStatement(query).use { stmt ->
                        val ps = this.readyParamsInQuery(stmt , params , paramsNames);
                        val resultExecute = ps?.executeQuery();
                        blockExecute(ExecuteResult.Success(resultExecute));
                    }
                }
            }
        )
    }


    override fun executeInsert(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Long>) -> Unit
    ) {
        this.execute(
            builtQuery = builtQuery ,
            blockExecute =  {
                conn , query , params , paramsNames , error ->
                if (error != null) {
                    blockExecute(ExecuteResult.Failure(error))
                    return@execute
                }
                else{
                    conn.prepareStatement(query , Statement.RETURN_GENERATED_KEYS).use { stmt ->
                        val ps = this.readyParamsInQuery(stmt , params , paramsNames);
                        val effectedRows = ps?.executeUpdate();
                        if (effectedRows != null && effectedRows > 0) {
                            ps.generatedKeys.use { keys ->
                                if (keys.next()){
                                    val newId = keys.getLong(1);
                                    blockExecute(ExecuteResult.Success(newId));
                                }
                                else{
                                    ExecuteResult.Failure(Throwable("No generated key returned"))
                                }
                            }
                        }
                    }
                }

            }
        )
    }


    override fun executeUpdate(
        builtQuery: BuiltQuery,
        blockExecute: (ExecuteResult<Int>) -> Unit
    ) {

        this.execute(
            builtQuery = builtQuery ,
            blockExecute =  {
                    conn , query , params , paramsNames , error ->
                if (error != null) {
                    blockExecute(ExecuteResult.Failure(error))
                    return@execute
                }
                else{
                    conn.prepareStatement(query ).use { stmt ->
                        val ps = this.readyParamsInQuery(stmt , params , paramsNames);
                        val effectedRows = ps?.executeUpdate();
                        blockExecute(ExecuteResult.Success(effectedRows));
                    }
                }
            }
        )

    }


    override fun executeDelete(
        builtQuery:   BuiltQuery,
        blockExecute: (ExecuteResult<Int>) -> Unit
    ) {

        this.execute(
            builtQuery = builtQuery ,
            blockExecute =  {
                    conn , query , params , paramsNames , error ->
                if (error != null) {
                    blockExecute(ExecuteResult.Failure(error))
                    return@execute
                }
                else{
                    conn.prepareStatement(query ).use { stmt ->
                        val ps = this.readyParamsInQuery(stmt , params , paramsNames);
                        val effectedRows = ps?.executeUpdate();
                        blockExecute(ExecuteResult.Success(effectedRows));
                    }
                }
            }
        )

    }

}