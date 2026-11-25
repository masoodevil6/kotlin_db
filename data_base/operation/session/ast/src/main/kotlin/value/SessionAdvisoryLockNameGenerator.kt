package gog.my_project.data_base_session.ast.value

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

object SessionAdvisoryLockNameGenerator {
    private const val DOMAIN = "kotlin_db:session-advisory-lock"
    private const val VERSION = 1
    private const val PREFIX = "kdb1_"
    private const val HASH_BYTES = 29
    private const val HEX_DIGITS = "0123456789abcdef"

    fun generate(resource: LockResource): String {
        val payload = ByteArrayOutputStream().use { byteOutput ->
            DataOutputStream(byteOutput).use { dataOutput ->
                writeField(dataOutput, DOMAIN)
                dataOutput.writeInt(VERSION)
                writeField(dataOutput, resource.namespace)
                writeField(dataOutput, resource.name)
            }
            byteOutput.toByteArray()
        }

        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        val output = CharArray(PREFIX.length + HASH_BYTES * 2)
        PREFIX.toCharArray().copyInto(output)

        for (index in 0 until HASH_BYTES) {
            val byte = digest[index].toInt() and 0xff
            output[PREFIX.length + index * 2] = HEX_DIGITS[byte ushr 4]
            output[PREFIX.length + index * 2 + 1] = HEX_DIGITS[byte and 0x0f]
        }

        return String(output)
    }

    private fun writeField(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeUInt32(output, bytes.size)
        output.write(bytes)
    }

    private fun writeUInt32(output: DataOutputStream, value: Int) {
        require(value >= 0) { "Encoded field length must not be negative." }
        output.writeByte(value ushr 24)
        output.writeByte(value ushr 16)
        output.writeByte(value ushr 8)
        output.writeByte(value)
    }
}
