package gog.my_project.data_base.query.renderer.column_method

import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AggregateColumnRenderingTest {
    @Test
    fun `sum and avg method ASTs render with their selected columns`() {
        val query = QueryRenderSelectBuilder()
        query.from { table("user_users").alias("uu") }
        query.select {
            addColumn {
                methodSum { column { tableSelector("uu"); columnSelector("age") } }
                alias("total_age")
            }
            addColumn {
                methodAvg { column { tableSelector("uu"); columnSelector("age") } }
                alias("average_age")
            }
        }

        val columns = query.ast.select!!.columns
        assertNotNull(columns[0].ColumnMethodSum?.Column)
        assertNotNull(columns[1].ColumnMethodAvg?.Column)

        val sql = requireNotNull(MySqlDialect().render(query.ast))
        assertTrue(sql.contains("sum( uu.age ) As total_age"), sql)
        assertTrue(sql.contains("avg( uu.age ) As average_age"), sql)
    }

    @Test
    fun `min method renders its over clause before output alias`() {
        val query = QueryRenderSelectBuilder()
        query.from { table("user_users").alias("uu") }
        query.select {
            addColumn {
                methodMin {
                    column { tableSelector("uu"); columnSelector("age") }
                    over { }
                }
                alias("user_age_min")
            }
        }

        val sql = requireNotNull(MySqlDialect().render(query.ast))
        assertTrue(sql.contains("min( uu.age ) over() As user_age_min"), sql)
    }

    @Test
    fun `count method renders its selected column`() {
        val query = QueryRenderSelectBuilder()
        query.from { table("user_users").alias("uu") }
        query.select {
            addColumn {
                methodCount {
                    addColumn { tableSelector("uu"); columnSelector("age") }
                }
                alias("user_age_count")
            }
        }

        val sql = requireNotNull(MySqlDialect().render(query.ast))
        assertTrue(sql.contains("count("), sql)
        assertTrue(sql.contains("uu.age"), sql)
        assertTrue(sql.contains("As user_age_count"), sql)
    }

    @Test
    fun `count method renders with its count data class`() {
        val query = QueryRenderSelectBuilder()
        query.from { table("user_users").alias("uu") }
        query.select {
            addColumn {
                methodCount {
                    distinct()
                    addColumn { tableSelector("uu"); columnSelector("age") }
                }
                alias("user_age_count")
            }
        }

        val sql = requireNotNull(MySqlDialect().render(query.ast))
        assertTrue(sql.contains("count("), sql)
        assertTrue(sql.contains("distinct"), sql)
        assertTrue(sql.contains("uu.age"), sql)
        assertTrue(sql.contains("As user_age_count"), sql)
    }
}
