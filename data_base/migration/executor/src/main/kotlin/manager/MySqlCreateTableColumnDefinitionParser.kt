package gog.my_project.data_base.migration.executor.manager

/** Extracts the exact text after a column identifier from SHOW CREATE TABLE output. */
internal object MySqlCreateTableColumnDefinitionParser {
    fun definitionSuffix(createTableSql: String, sourceColumn: String): String {
        val body = tableDefinitionBody(createTableSql)
        var found: String? = null
        for (fragment in splitTopLevelDefinitions(body)) {
            val identifier = readIdentifier(fragment) ?: continue
            if (identifier.first == sourceColumn) {
                val suffix = fragment.substring(identifier.second)
                require(suffix.isNotBlank() && suffix.first().isWhitespace()) {
                    "Source column definition for '$sourceColumn' is incomplete"
                }
                require(found == null) { "Column '$sourceColumn' appears more than once in table definition" }
                found = suffix
            }
        }
        return found ?: throw IllegalArgumentException("Column '$sourceColumn' was not found in SHOW CREATE TABLE output")
    }

    private fun tableDefinitionBody(sql: String): String {
        require(sql.trimStart().startsWith("CREATE TABLE", ignoreCase = true)) {
            "Unrecognized SHOW CREATE TABLE output"
        }
        var quote: Char? = null
        var i = 0
        while (i < sql.length) {
            val char = sql[i]
            if (quote != null) {
                if (char == '\\' && quote != '`' && i + 1 < sql.length) {
                    i += 2
                    continue
                }
                if (char == quote) {
                    if (i + 1 < sql.length && sql[i + 1] == quote) {
                        i += 2
                        continue
                    }
                    quote = null
                }
            } else if (char == '`' || char == '\'' || char == '"') {
                quote = char
            } else if (char == '/' && sql.getOrNull(i + 1) == '*') {
                i = skipBlockComment(sql, i)
                continue
            } else if (char == '#' || (char == '-' && sql.getOrNull(i + 1) == '-')) {
                i = skipLineComment(sql, i)
                continue
            } else if (char == '(') {
                val end = matchingTableClose(sql, i)
                return sql.substring(i + 1, end)
            }
            i++
        }
        throw IllegalArgumentException("CREATE TABLE body is missing")
    }

    private fun matchingTableClose(sql: String, openIndex: Int): Int {
        var depth = 1
        var quote: Char? = null
        var i = openIndex + 1
        while (i < sql.length) {
            val char = sql[i]
            if (quote != null) {
                when {
                    char == '\\' && quote != '`' && i + 1 < sql.length -> i += 2
                    char == quote && i + 1 < sql.length && sql[i + 1] == quote -> i += 2
                    char == quote -> { quote = null; i++ }
                    else -> i++
                }
                continue
            }
            if (char == '/' && sql.getOrNull(i + 1) == '*') {
                i = skipBlockComment(sql, i)
                continue
            }
            if (char == '#' || (char == '-' && sql.getOrNull(i + 1) == '-')) {
                i = skipLineComment(sql, i)
                continue
            }
            when {
                char == '`' || char == '\'' || char == '"' -> quote = char
                char == '(' -> depth++
                char == ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        throw IllegalArgumentException("CREATE TABLE body is not balanced")
    }

    private fun splitTopLevelDefinitions(body: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var quote: Char? = null
        var start = 0
        var i = 0
        while (i < body.length) {
            val char = body[i]
            if (quote != null) {
                when {
                    char == '\\' && quote != '`' && i + 1 < body.length -> i += 2
                    char == quote && i + 1 < body.length && body[i + 1] == quote -> i += 2
                    char == quote -> { quote = null; i++ }
                    else -> i++
                }
                continue
            }
            if (char == '/' && body.getOrNull(i + 1) == '*') {
                i = skipBlockComment(body, i)
                continue
            }
            if (char == '#' || (char == '-' && body.getOrNull(i + 1) == '-')) {
                i = skipLineComment(body, i)
                continue
            }
            when {
                char == '`' || char == '\'' || char == '"' -> quote = char
                char == '(' -> depth++
                char == ')' -> {
                    depth--
                    require(depth >= 0) { "Malformed CREATE TABLE definition" }
                }
                char == ',' && depth == 0 -> {
                    result += body.substring(start, i)
                    start = i + 1
                }
            }
            i++
        }
        require(quote == null && depth == 0) { "Malformed CREATE TABLE definition" }
        result += body.substring(start)
        return result
    }

    /** Returns the decoded identifier and the first index after its source spelling. */
    private fun readIdentifier(fragment: String): Pair<String, Int>? {
        val start = fragment.indexOfFirst { !it.isWhitespace() }
        if (start < 0) return null
        if (fragment[start] != '`') {
            val end = fragment.indexOfFirstFrom(start) { it.isWhitespace() }
                .let { if (it < 0) fragment.length else it }
            return fragment.substring(start, end) to end
        }

        val decoded = StringBuilder()
        var i = start + 1
        while (i < fragment.length) {
            if (fragment[i] == '`') {
                if (i + 1 < fragment.length && fragment[i + 1] == '`') {
                    decoded.append('`')
                    i += 2
                    continue
                }
                return decoded.toString() to (i + 1)
            }
            decoded.append(fragment[i])
            i++
        }
        throw IllegalArgumentException("Unclosed column identifier in CREATE TABLE definition")
    }

    private inline fun String.indexOfFirstFrom(startIndex: Int, predicate: (Char) -> Boolean): Int {
        for (index in startIndex until length) if (predicate(this[index])) return index
        return -1
    }

    private fun skipBlockComment(text: String, start: Int): Int {
        val end = text.indexOf("*/", start + 2)
        require(end >= 0) { "Unclosed comment in CREATE TABLE definition" }
        return end + 2
    }

    private fun skipLineComment(text: String, start: Int): Int {
        val end = text.indexOf('\n', start)
        return if (end < 0) text.length else end + 1
    }
}
