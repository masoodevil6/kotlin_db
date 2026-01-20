package gog.my_project.data_base.connection.tools.datas


import gog.my_project.data_base.connection.tools.objects.SqlTypeResolver
import java.sql.PreparedStatement
import java.sql.SQLException

data class SqlParameter<T>(
    val name: String,
    val value: T?,
    val sqlType: Int
) {

    @Throws(SQLException::class)
    fun bind(ps: PreparedStatement, index: Int) {
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