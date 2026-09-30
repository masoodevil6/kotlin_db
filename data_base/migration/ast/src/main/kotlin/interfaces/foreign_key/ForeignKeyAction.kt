package gog.my_project.data_base.migration.ast.interfaces.foreign_key

/** Referential actions supported by the v1.13 MySQL/MariaDB Foreign Key contract. */
enum class ForeignKeyAction {
    CASCADE,
    RESTRICT,
    NO_ACTION,
    SET_NULL,
}
