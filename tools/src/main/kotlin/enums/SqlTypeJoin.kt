package gog.my_project.enums

enum class SqlTypeJoin(val value: String) {
    InnerJoin("join"),
    LeftJoin("left join"),
    RightJoin("right join")
}