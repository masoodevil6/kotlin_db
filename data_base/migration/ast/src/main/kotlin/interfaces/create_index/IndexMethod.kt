package gog.my_project.data_base.migration.ast.interfaces.create_index

/** Index access method requested in CREATE INDEX SQL. The database engine decides whether it can use it. */
enum class IndexMethod {
    BTREE,
    HASH,
}
