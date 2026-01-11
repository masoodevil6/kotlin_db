package gog.my_project.tools.scripts

object StringTools {

    fun normalizeSpaces(sql: String): String {
        return sql
            .replace("\\s+".toRegex(), " ")     // هر فاصله تکراری رو یکی کن
            .replace("\\( ".toRegex(), "(")     // پرانتز چپ تمیز
            .replace(" \\)".toRegex(), ")")     // پرانتز راست تمیز
            .replace(" ,".toRegex(), ",")       // کاما قبلش فاصله نداشته باشه
            .trim()
    }


    fun formatSql(sql: String): String {
        val tokens = sql
            .replace("(", " ( ")
            .replace(")", " ) ")
            .replace(",", " , ")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }

        val breakBefore = setOf(
            "SELECT", "FROM", "WHERE", "JOIN", "GROUP", "ORDER",
            "INNER", "LEFT", "RIGHT", "ON",
            "LIMIT", "OFFSET", "WITH", "AND", "OR"
        )

        val sb = kotlin.text.StringBuilder()
        var indent = 0
        var i = 0

        while (i < tokens.size) {
            var tok = tokens[i]

            // کلمات کلیدی بزرگ
            if (tok.matches(Regex("[a-zA-Z]+"))) {
                tok = tok.uppercase()
            }

            if (tok == ")") {
                indent--
            }

            // خط جدید قبل از کلمات کلیدی
            if (tok in breakBefore || tok == ")") {
                sb.append("\n")
                sb.append("    ".repeat(indent))
            }

            sb.append(tok)

            // کاما = خط بعدی
            if (tok == ",") {
                sb.append("\n")
                sb.append("    ".repeat(indent))
            } else {
                sb.append(" ")
            }

            // بعد از ( باید indent اضافه شود
            if (tok == "(") {
                indent++
            }

            i++
        }

        return sb.toString().trim()
    }



}