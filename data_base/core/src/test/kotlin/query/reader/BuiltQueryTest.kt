package gog.my_project.data_base.core.query.reader

import java.lang.reflect.Proxy
import java.sql.PreparedStatement
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuiltQueryTest {
    @Test
    fun replacesEveryPlaceholderOccurrenceInTextOrderWithoutPrefixCollision() {
        val query = BuiltQuery(
            "SELECT :id2, :id, :id2, :other",
            mutableListOf(
                SqlParameter.of("id", 1),
                SqlParameter.of("id2", 2),
                SqlParameter.of("other", 3),
            ),
        )

        assertEquals(listOf("id2", "id", "id2", "other"), query.getListParamNames())
        assertEquals("SELECT ?, ?, ?, ?", query.getReadyQuery())
        assertEquals(
            listOf(2, 1, 2, 3),
            query.getListParamNames().map { name -> query.params.first { it.name == name }.value },
        )
    }

    @Test
    fun permitsAnEmptyParameterListWhenThereAreNoPlaceholders() {
        val query = BuiltQuery("SELECT 1", mutableListOf())

        assertEquals(emptyList(), query.getListParamNames())
        assertEquals("SELECT 1", query.getReadyQuery())
    }

    @Test
    fun rejectsMissingAndUnusedParameters() {
        assertFailsWith<IllegalArgumentException> {
            BuiltQuery("SELECT :id", mutableListOf()).getListParamNames()
        }
        assertFailsWith<IllegalArgumentException> {
            BuiltQuery("SELECT 1", mutableListOf(SqlParameter.of("unused", 1))).getListParamNames()
        }
    }

    @Test
    fun acceptsEqualDuplicateParametersAndRejectsConflicts() {
        val equal = BuiltQuery(
            "SELECT :id, :id",
            mutableListOf(SqlParameter.of("id", 7), SqlParameter.of("id", 7)),
        )
        assertEquals(listOf(7, 7), equal.getListParamNames().map { name -> equal.params.first { it.name == name }.value })

        assertFailsWith<IllegalArgumentException> {
            BuiltQuery(
                "SELECT :id, :id",
                mutableListOf(SqlParameter.of("id", 7), SqlParameter.of("id", 8)),
            ).getListParamNames()
        }
        assertFailsWith<IllegalArgumentException> {
            BuiltQuery(
                "SELECT :id, :id",
                mutableListOf(
                    SqlParameter.of("id", 7),
                    SqlParameter("id", 7L, Types.BIGINT),
                ),
            ).getListParamNames()
        }
    }

    @Test
    fun nullQueryRetainsNullConversionAndSkipsParameterValidation() {
        val query = BuiltQuery(null, mutableListOf(SqlParameter.of("unused", 1)))

        assertEquals(null, query.getReadyQuery())
        assertEquals(emptyList(), query.getListParamNames())
    }

    @Test
    fun sqlParameterBindUsesCanonicalJdbcOverloadsForSupportedTypes() {
        data class Binding(val method: String, val args: List<Any?>)
        val calls = mutableListOf<Binding>()
        val statement = Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java),
        ) { _, method, args ->
            if (method.name == "setObject" || method.name == "setNull") {
                calls += Binding(method.name, args.orEmpty().toList())
            }
            null
        } as PreparedStatement

        val values = listOf(
            1 to Types.INTEGER,
            2L to Types.BIGINT,
            3.0 to Types.DOUBLE,
            4.0f to Types.FLOAT,
            true to Types.BOOLEAN,
            "text" to Types.VARCHAR,
            LocalDate.of(2026, 10, 5) to Types.DATE,
            LocalTime.of(12, 30) to Types.TIME,
            LocalDateTime.of(2026, 10, 5, 12, 30) to Types.TIMESTAMP,
            Instant.parse("2026-10-05T12:30:00Z") to Types.TIMESTAMP,
            Any() to Types.OTHER,
        )

        values.forEachIndexed { index, (value, sqlType) ->
            SqlParameter.of("p$index", value).bind(statement, index + 1)
        }
        SqlParameter.nullOf("typedNull", Types.VARCHAR).bind(statement, 12)
        SqlParameter.nullOf("untypedNull", Types.NULL).bind(statement, 13)

        assertEquals(values.size, calls.take(values.size).size)
        values.forEachIndexed { index, (value, sqlType) ->
            assertEquals(Binding("setObject", listOf(index + 1, value, sqlType)), calls[index])
        }
        assertEquals(Binding("setNull", listOf(12, Types.VARCHAR)), calls[11])
        assertEquals(Binding("setNull", listOf(13, Types.NULL)), calls[12])
    }
}
