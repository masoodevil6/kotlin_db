package gog.my_project.data_base.core.query.reader

import java.sql.PreparedStatement
import java.sql.SQLException

data class SqlParameter<T>(
    override val name: String,
    override val value: T?,
    override val sqlType: Int
) : IQueryParameterBinding {

    @Throws(SQLException::class)
    override fun bind(ps: PreparedStatement, index: Int) {
        if (value == null) {
            ps.setNull(index, sqlType)
        } else {
            ps.setObject(index, value, sqlType)
        }
    }

    companion object {
        fun <T> of(name: String, value: T): SqlParameter<T> =
            SqlParameter(name, value, SqlTypeResolver.resolve(value))

        fun nullOf(name: String, sqlType: Int): SqlParameter<Nothing> =
            SqlParameter(name, null, sqlType)
    }
}
