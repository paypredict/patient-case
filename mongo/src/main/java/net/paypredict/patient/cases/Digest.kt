package net.paypredict.patient.cases

import java.io.File
import java.security.MessageDigest
import javax.json.*

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/24/2018.
 */
fun MessageDigest.toHexString(): String =
    digest().joinToString(separator = "") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

fun ByteArray.toDigest(): MessageDigest =
    sha().also { it.update(this) }

fun File.toDigest(): MessageDigest =
    sha().also { it.update(readBytes()) }

fun JsonObject.toDigest(): MessageDigest =
    sha().also { updateDigest(it) }

fun File.digest(): String =
    toDigest().toHexString()

private fun JsonValue?.updateDigest(digest: MessageDigest) {
    when (this) {
        null -> {
            digest.update(0)
        }
        is JsonObject -> {
            digest.update('j'.toByte())
            keys.sorted().forEach { key ->
                digest.update('k'.toByte())
                digest.update(key.toByteArray())
                this[key].updateDigest(digest)
            }
        }
        is JsonArray -> {
            digest.update('a'.toByte())
            this.forEach { item ->
                digest.update('i'.toByte())
                item.updateDigest(digest)
            }
        }
        is JsonNumber -> {
            digest.update('n'.toByte())
            digest.update(this.valueType.name.toByteArray())
            digest.update(this.toString().toByteArray())
        }
        is JsonString -> {
            digest.update('s'.toByte())
            digest.update(this.string.toByteArray())
        }
        JsonValue.NULL -> {
            digest.update('N'.toByte())
        }
        JsonValue.TRUE -> {
            digest.update('T'.toByte())
        }
        JsonValue.FALSE -> {
            digest.update('F'.toByte())
        }
    }
}

private fun sha() = MessageDigest.getInstance("SHA")
