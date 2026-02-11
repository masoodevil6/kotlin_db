package gog.my_project.data_base.query.api.tools.enums

enum class SqlTypeJoin(val value: String) {
    InnerJoin("join"),
    LeftJoin("left join"),
    RightJoin("right join")
}