package gog.my_project.data_base.query.example.v1.queries

import gog.my_project.data_base.core.query.reader.BuiltQuery

internal fun printMutationSqlV1(
    title: String,
    query: BuiltQuery,
) {
    println("\n=============================================\n$title\nquery: ${query.query}\nparams: ${query.params}")
}
