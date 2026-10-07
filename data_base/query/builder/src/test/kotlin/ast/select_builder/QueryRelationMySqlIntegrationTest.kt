package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.manager.QueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.relations.queryRelation
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class QueryRelationMySqlIntegrationTest {

    @Test
    fun `nested relation query executes with ordered JDBC parameter binding`() {
        val host = System.getProperty("execute.test.db.host")
        val database = System.getProperty("execute.test.db.name")
        val username = System.getProperty("execute.test.db.username")
        assumeTrue(!host.isNullOrBlank() && !database.isNullOrBlank() && !username.isNullOrBlank()) {
            "Set execute.test.db.host, execute.test.db.name, and execute.test.db.username Gradle project properties to enable QueryRelation MySQL integration"
        }

        val port = System.getProperty("execute.test.db.port")?.toIntOrNull() ?: 3306
        val password = System.getProperty("execute.test.db.password").orEmpty()
        val previousConfig = DefaultDatabaseConfig.config
        val config = DatabaseConfig(
            dbDomain = "jdbc:mysql://$host",
            dbPort = port,
            dbName = database!!,
            dbUserName = username!!,
            dbPassword = password,
        )
        val tableName = "plan3_query_relation_${UUID.randomUUID().toString().replace('-', '_')}"
        var connection: java.sql.Connection? = null
        var cleanupRequired = false
        var primaryFailure: Throwable? = null

        try {
            DefaultDatabaseConfig.config = config
            val activeConnection = DriverManager.getConnection(config.getDbUrl(), username, password)
            connection = activeConnection
            val metadata = activeConnection.metaData
            println("PLAN3_MYSQL_SERVER_VERSION=${metadata.databaseProductVersion}")
            println("PLAN3_CONNECTOR_VERSION=${metadata.driverName} ${metadata.driverVersion}")
            println("PLAN3_JAVA_VERSION=${System.getProperty("java.version")}")

            cleanupRequired = true
            activeConnection.createStatement().use { statement ->
                statement.execute("CREATE TABLE $tableName (id BIGINT NOT NULL PRIMARY KEY)")
            }
            activeConnection.prepareStatement("INSERT INTO $tableName (id) VALUES (?)").use { statement ->
                statement.setLong(1, 10L)
                assertEquals(1, statement.executeUpdate())
            }

            val relationA = queryRelation("live_relation_a") {
                table { table(tableName).alias("source_row") }
                select {
                    addColumn {
                        column { tableColumn("source_row", "id") }
                        alias("a_id")
                    }
                }
                where {
                    conditions {
                        addCondition {
                            logicalAnd()
                            sideSelector { tableColumn("source_row", "id") }
                            operationGreaterThanOrEqual()
                            sideValue("a", 5L)
                        }
                    }
                }
            }
            val relationB = queryRelation("live_relation_b") {
                from(relationA)
                select {
                    addColumn {
                        relationColumn(relationA, "a_id")
                        alias("b_id")
                    }
                }
                where {
                    conditions {
                        addCondition {
                            logicalAnd()
                            sideSelector { tableColumn(relationA.name, "a_id") }
                            operationLessThenOrEqual()
                            sideValue("b", 20L)
                        }
                    }
                }
            }
            val relationC = queryRelation("live_relation_c") {
                from(relationB)
                select {
                    addColumn {
                        relationColumn(relationB, "b_id")
                        alias("id")
                    }
                }
                where {
                    conditions {
                        addCondition {
                            logicalAnd()
                            sideSelector { tableColumn(relationB.name, "b_id") }
                            operationEqual()
                            sideValue("c", 10L)
                        }
                    }
                }
            }
            val query = QueryRenderSelectBuilder()
                .from(relationC, "consumer_c")
                .select { addColumn { relationColumn(relationC, "id") } }
                .where {
                    conditions {
                        addCondition {
                            logicalAnd()
                            sideSelector { tableColumn("consumer_c", "id") }
                            operationLessThen()
                            sideValue("d", 11L)
                        }
                    }
                }

            assertEquals(listOf("a", "b", "c", "d"), query.params.map { it.name })
            assertEquals(listOf(5L, 20L, 10L, 11L), query.params.map { it.value })
            val sql = MySqlDialect().render(query.ast)!!
            assertFalse(Regex("\\b(null|undefined)\\b", RegexOption.IGNORE_CASE).containsMatchIn(sql), sql)
            val cteA = sql.indexOf("live_relation_a AS", ignoreCase = true)
            val cteB = sql.indexOf("live_relation_b AS", cteA + 1, ignoreCase = true)
            val cteC = sql.indexOf("live_relation_c AS", cteB + 1, ignoreCase = true)
            assertTrue(cteA >= 0 && cteB > cteA && cteC > cteB, sql)
            assertTrue(Regex("FROM\\s+live_relation_c\\s+As\\s+consumer_c", RegexOption.IGNORE_CASE).containsMatchIn(sql), sql)
            println("PLAN3_GENERATED_SQL=$sql")

            val builtQuery = BuiltQuery(sql, query.params.toMutableList())
            var callbackCount = 0
            val rows = mutableListOf<Long>()
            QueryExecute().executeSelect(builtQuery) { result ->
                callbackCount += 1
                when (result) {
                    is ExecuteResult.Success -> {
                        val resultSet = result.result ?: error("QueryExecute returned a null ResultSet")
                        resultSet.use {
                            assertTrue(it.next(), "Expected the nested relation query to return a row")
                            rows += it.getLong("id")
                            assertFalse(it.next(), "Expected exactly one row")
                        }
                    }
                    is ExecuteResult.Failure -> throw result.exception
                }
            }

            assertEquals(1, callbackCount)
            assertEquals(listOf(10L), rows)
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try {
                if (cleanupRequired) {
                    val cleanupConnection = connection
                    if (cleanupConnection != null) {
                        cleanupConnection.createStatement().use { it.execute("DROP TABLE IF EXISTS $tableName") }
                    }
                }
            } catch (cleanupFailure: Throwable) {
                if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure) else throw cleanupFailure
            } finally {
                try {
                    connection?.close()
                } catch (closeFailure: Throwable) {
                    if (primaryFailure != null) primaryFailure.addSuppressed(closeFailure) else throw closeFailure
                } finally {
                    DefaultDatabaseConfig.config = previousConfig
                }
            }
        }
    }
}
