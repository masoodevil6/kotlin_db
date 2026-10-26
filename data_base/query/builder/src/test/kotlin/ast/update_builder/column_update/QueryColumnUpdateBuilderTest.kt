package gog.my_project.data_base.query.builder.ast.update_builder.column_update

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

@QBTable(name = "update_people", alias = "up")
private class UpdateModel : IModelBase {
    @QBColumn(name = "updated_amount")
    val amount: Int get() = error("UPDATE metadata resolution must not invoke the getter")
}

class QueryColumnUpdateBuilderTest {
    @Test
    fun `setValue stores an independent parameter AST and defaults its name from the column tag`() {
        val update = QueryRenderUpdateBuilder().addValue {
            column("amount")
            setValue { paramValue(12) }
        }

        val column = update.ast.columns.single()
        val parameter = assertIs<IQueryParameterAst<*>>(column.parameterAst)
        assertEquals("amount", column.metadata.columnName)
        assertEquals("amount", column.metadata.columnTag)
        assertEquals("amount", parameter.metadata.name)
        assertEquals(12, parameter.metadata.value)
        assertEquals(Types.INTEGER, parameter.metadata.sqlType)
    }

    @Test
    fun `model column resolves annotation and caller alias before independent setValue`() {
        val defaultAliasUpdate = QueryRenderUpdateBuilder().addValue {
            column(UpdateModel::class, UpdateModel::amount)
            setValue { paramValue(20) }
        }
        assertEquals("up", defaultAliasUpdate.ast.columns.single().metadata.columnAlias)
        assertEquals("up_updated_amount", defaultAliasUpdate.ast.columns.single().parameterAst?.metadata?.name)

        val update = QueryRenderUpdateBuilder().addValue {
            column(UpdateModel::class, UpdateModel::amount, "caller_alias")
            setValue {
                paramName("amount_value")
                paramValue(21)
            }
        }

        val column = update.ast.columns.single()
        assertEquals("caller_alias", column.metadata.columnAlias)
        assertEquals("updated_amount", column.metadata.columnName)
        assertEquals("caller_alias_updated_amount", column.metadata.columnTag)
        assertEquals("amount_value", column.parameterAst?.metadata?.name)
        assertEquals(21, column.parameterAst?.metadata?.value)
    }

    @Test
    fun `setValue requires a selected column and a parameter value`() {
        assertFailsWith<IllegalStateException> {
            QueryColumnUpdateBuilder().setValue { paramValue(1) }
        }

        val missingValue = QueryColumnUpdateBuilder().apply { column("amount") }
        assertFailsWith<IllegalStateException> {
            missingValue.setValue { paramName("amount_value") }
        }
        assertNull(missingValue.ast.parameterAst)
    }
}
