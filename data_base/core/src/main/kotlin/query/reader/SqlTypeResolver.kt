package gog.my_project.data_base.core.query.reader

import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object SqlTypeResolver {

    fun resolve(value: Any?): Int =
        when (value) {
            null -> Types.NULL
            is Int -> Types.INTEGER
            is Long -> Types.BIGINT
            is Double -> Types.DOUBLE
            is Float -> Types.FLOAT
            is Boolean -> Types.BOOLEAN
            is String -> Types.VARCHAR
            is LocalDate -> Types.DATE
            is LocalTime -> Types.TIME
            is LocalDateTime -> Types.TIMESTAMP
            is Instant -> Types.TIMESTAMP
            else -> Types.OTHER
        }
}