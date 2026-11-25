package gog.my_project.data_base_session.ast.value

data class LockResource(
    val namespace: String,
    val name: String,
) {
    init {
        require(namespace.isNotEmpty()) { "Lock namespace must not be empty." }
        require(name.isNotEmpty()) { "Lock name must not be empty." }
    }
}
