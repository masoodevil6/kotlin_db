package gog.my_project.data_base.core.query.reader

import java.sql.PreparedStatement
import java.sql.SQLException

/** Execution contract for one logical query parameter definition or rendered occurrence. */
interface IQueryParameterBinding {
    val name: String
    val value: Any?
    val sqlType: Int

    @Throws(SQLException::class)
    fun bind(ps: PreparedStatement, index: Int)
}

/** Immutable execution snapshot created from an independent parameter AST definition. */
data class QueryParameterBinding(
    override val name: String,
    override val value: Any?,
    override val sqlType: Int,
) : IQueryParameterBinding {
    @Throws(SQLException::class)
    override fun bind(ps: PreparedStatement, index: Int) {
        if (value == null) {
            ps.setNull(index, sqlType)
        } else {
            ps.setObject(index, value, sqlType)
        }
    }
}
