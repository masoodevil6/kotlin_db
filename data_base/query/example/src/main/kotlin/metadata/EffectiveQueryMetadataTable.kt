package gog.my_project.data_base.query.example.metadata

import gog.my_project.data_base.query.api.metadata.EffectiveQueryMetadata

/** Formats effective SELECT metadata as console tables. */
internal fun formatEffectiveQueryMetadata(metadata: EffectiveQueryMetadata): String = buildString {
    appendLine("meta_data")
    appendLine()
    appendLine("Final SELECT outputs")
    if (metadata.outputs.isEmpty()) {
        append("<none>")
    } else {
        append(
            formatTable(
                headers = listOf(
                    "#",
                    "tableName",
                    "tableAlias",
                    "logicalOutputName",
                    "publishedSqlAlias",
                    "executionType",
                ),
                rows = metadata.outputs.mapIndexed { index, output ->
                    listOf(
                        (index + 1).toString(),
                        output.tableName.toMetadataCell(),
                        output.tableAlias.toMetadataCell(),
                        output.logicalOutputName.toMetadataCell(),
                        output.publishedSqlAlias.toMetadataCell(),
                        output.executionType?.name.toMetadataCell(),
                    )
                },
            ),
        )
    }
}

private fun String?.toMetadataCell(): String = when {
    this == null -> "<null>"
    this.isEmpty() -> "<empty>"
    else -> replace("\r\n", " [newline] ").replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').replace('|', '/')
}

private fun formatTable(headers: List<String>, rows: List<List<String>>): String {
    val widths = headers.indices.map { column ->
        maxOf(headers[column].length, rows.maxOfOrNull { it[column].length } ?: 0)
    }
    val border = widths.joinToString(separator = "+", prefix = "+", postfix = "+") {
        "-".repeat(it + 2)
    }
    fun formatRow(cells: List<String>): String = cells.indices.joinToString(
        separator = "|",
        prefix = "|",
        postfix = "|",
    ) { column -> " " + cells[column].padEnd(widths[column]) + " " }

    return buildString {
        appendLine(border)
        appendLine(formatRow(headers))
        appendLine(border)
        rows.forEach { appendLine(formatRow(it)) }
        append(border)
    }
}
