package gog.my_project.data_base_manager.facade

/**
 * Type-safe value key for one database registration in a [DatabaseManager].
 *
 * Registration, duplicate detection, lookup, and context caching use [equals] and [hashCode].
 * Therefore, keys that compare equal identify the same registration, including equal instances
 * of a data class. Implementations should be immutable and keep their equality and hash code
 * stable for as long as they are registered. A key's identity is independent of its
 * [gog.my_project.data_base_operation.config.data_base.DatabaseConfig]: distinct keys may use equal configs.
 *
 * Ordinary classes that do not override [equals] and [hashCode] retain reference-based identity;
 * objects are singleton keys.
 */
interface DatabaseKey
