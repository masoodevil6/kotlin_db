package gog.my_project.data_base_migration.core.tags

internal fun validatedTagName(name: String, description: String, maxLength: Int? = null): String {
    require(name.isNotBlank()) { "$description must not be blank" }
    if (maxLength != null) {
        require(name.length <= maxLength) { "$description must not exceed $maxLength characters" }
    }
    return name
}
