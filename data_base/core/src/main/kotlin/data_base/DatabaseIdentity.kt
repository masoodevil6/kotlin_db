package gog.my_project.data_base_operation.config.data_base

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

/**
 * A deterministic fingerprint of the configured database destination, excluding credentials.
 *
 * This identifies the canonical endpoint represented by [DatabaseConfig], not necessarily the
 * physical server reached by a custom connection provider, proxy, DNS alias, or cluster router.
 * Unsupported or ambiguous JDBC URLs return `null` from [from] so callers can fail closed.
 */
class DatabaseIdentity private constructor(
    /** Versioned SHA-256 fingerprint; contains no host/database fields or credentials in plaintext. */
    val value: String,
) {
    override fun equals(other: Any?): Boolean = other is DatabaseIdentity && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "DatabaseIdentity($value)"

    companion object {
        private const val DEFAULT_MYSQL_PORT = 3306
        private const val IDENTITY_VERSION = 1

        /**
         * Builds an identity for supported single-endpoint MySQL-family JDBC URLs.
         *
         * The current connection settings do not model routing parameters separately. URLs with
         * query parameters, multi-host authorities, credentials, unsupported schemes, malformed
         * endpoints, or ambiguous catalog paths are therefore rejected instead of guessed.
         */
        @JvmStatic
        fun from(config: DatabaseConfig): DatabaseIdentity? {
            val target = canonicalTarget(config.connection) ?: return null
            val encoded = encodeTarget(target)
            val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
            val hex = digest.joinToString(separator = "") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
            return DatabaseIdentity("db-target-v$IDENTITY_VERSION:$hex")
        }

        private data class CanonicalTarget(
            val host: String,
            val port: Int,
            val catalog: String,
        )

        private data class ParsedAuthority(
            val host: String,
            val port: Int?,
        )

        private fun canonicalTarget(connection: DatabaseConnectionConfig): CanonicalTarget? {
            val databaseName = connection.dbName
            if (
                databaseName.isBlank() ||
                databaseName.any { it in 'A'..'Z' } ||
                !databaseName.matches(Regex("[A-Za-z0-9_.$-]+"))
            ) return null

            val jdbcUrl = connection.getDbUrl()
            if (!jdbcUrl.startsWith("jdbc:", ignoreCase = true)) return null

            val uri = try {
                URI(jdbcUrl.substring(5))
            } catch (_: Exception) {
                return null
            }

            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            if (scheme != "mysql" && scheme != "mariadb") return null
            if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null

            val authority = uri.rawAuthority ?: return null
            val parsedAuthority = parseAuthority(authority) ?: return null
            val configuredPort = connection.dbPort
            if (configuredPort != null && configuredPort !in 1..65535) return null
            if (configuredPort != null && parsedAuthority.port != configuredPort) return null

            val effectivePort = parsedAuthority.port ?: DEFAULT_MYSQL_PORT
            if (effectivePort !in 1..65535) return null

            if (uri.rawPath != "/$databaseName") return null

            return CanonicalTarget(
                host = parsedAuthority.host,
                port = effectivePort,
                catalog = databaseName,
            )
        }

        private fun parseAuthority(authority: String): ParsedAuthority? {
            if (authority.isBlank() || '@' in authority || ',' in authority) return null

            val rawHost: String
            val rawPort: String?
            if (authority.startsWith('[')) {
                val closingBracket = authority.indexOf(']')
                if (closingBracket <= 1) return null
                rawHost = authority.substring(1, closingBracket)
                val suffix = authority.substring(closingBracket + 1)
                rawPort = when {
                    suffix.isEmpty() -> null
                    suffix.startsWith(':') && suffix.length > 1 -> suffix.substring(1)
                    else -> return null
                }
                if ('%' in rawHost) return null // Zone identifiers are machine-local routing data.
            } else {
                val colonCount = authority.count { it == ':' }
                if (colonCount > 1) return null // IPv6 literals must use brackets in a JDBC URL.
                val colon = authority.lastIndexOf(':')
                if (colon >= 0) {
                    rawHost = authority.substring(0, colon)
                    rawPort = authority.substring(colon + 1)
                    if (rawPort.isEmpty()) return null
                } else {
                    rawHost = authority
                    rawPort = null
                }
            }

            val port = rawPort?.toIntOrNull() ?: if (rawPort == null) null else return null
            if (port != null && port !in 1..65535) return null
            val host = normalizeHost(rawHost) ?: return null
            return ParsedAuthority(host, port)
        }

        private fun normalizeHost(rawHost: String): String? {
            if (rawHost.isBlank() || rawHost != rawHost.trim()) return null

            if (':' in rawHost) {
                val address = try {
                    InetAddress.getByName(rawHost)
                } catch (_: Exception) {
                    return null
                }
                if (address !is Inet6Address) return null
                val hex = address.address.joinToString(separator = "") { byte ->
                    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
                }
                return "ipv6:$hex"
            }

            val hostWithoutRootDot = rawHost.removeSuffix(".")
            if (hostWithoutRootDot.isBlank() || hostWithoutRootDot.endsWith('.')) return null

            if (hostWithoutRootDot.all { it.isDigit() || it == '.' }) {
                val octets = hostWithoutRootDot.split('.')
                if (octets.size != 4) return null
                val values = octets.map { octet ->
                    if (octet.isEmpty() || (octet.length > 1 && octet.startsWith('0'))) return null
                    octet.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
                }
                return "ipv4:${values.joinToString(".")}"
            }

            val ascii = try {
                IDN.toASCII(hostWithoutRootDot, IDN.USE_STD3_ASCII_RULES)
            } catch (_: IllegalArgumentException) {
                return null
            }
            if (ascii.isBlank() || ascii.length > 253) return null
            return "dns:${ascii.lowercase(Locale.ROOT)}"
        }

        private fun encodeTarget(target: CanonicalTarget): ByteArray {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { output ->
                writeField(output, "textqb.database-target")
                output.writeInt(IDENTITY_VERSION)
                writeField(output, target.host)
                output.writeInt(target.port)
                writeField(output, target.catalog)
                // Reserved, length-delimited routing section. No routing parameters are currently
                // represented by DatabaseConnectionConfig; unsupported URL parameters fail closed.
                output.writeInt(0)
            }
            return bytes.toByteArray()
        }

        private fun writeField(output: DataOutputStream, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            output.writeInt(bytes.size)
            output.write(bytes)
        }
    }
}
