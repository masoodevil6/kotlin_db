package gog.my_project.data_base_query.api.tools.enums

enum class SqlMethodColumn (val value: String) {
    Sum("sum"),
    Count("count"),
    Avg("avg"),
    Min("min"),
    Max("max"),


    Concat("concat"),
}