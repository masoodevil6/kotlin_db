package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TypedSelectOutputContractTest {

    @Test
    fun `execution type is stored on its own select item`() {
        val query = QueryRenderSelectBuilder().select {
            addColumn {
                column { tableColumn("uu", "id") }
                alias("user_id")
                execute(DataType.LONG)
            }
            addColumn {
                column { tableColumn("uu", "name") }
                alias("user_name")
                execute(DataType.STRING)
            }
        }

        val outputs = query.ast.select!!.columns
        assertEquals("user_id", outputs[0].ColumnAlias)
        assertEquals(DataType.LONG, outputs[0].ExecutionType)
        assertEquals("user_name", outputs[1].ColumnAlias)
        assertEquals(DataType.STRING, outputs[1].ExecutionType)
    }

    @Test
    fun `execution type is absent until explicitly declared`() {
        val query = QueryRenderSelectBuilder().select {
            addColumn {
                column { tableColumn("uu", "id") }
                alias("user_id")
            }
        }

        assertNull(query.ast.select!!.columns.single().ExecutionType)
    }
}
