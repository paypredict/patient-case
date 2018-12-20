package net.paypredict.patient.cases.pokitdok.client

import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.message.BasicNameValuePair
import java.io.ByteArrayOutputStream
import java.util.*

fun main(args: Array<String>) {
    val boundary = UUID.randomUUID().toString()
    val entity = MultipartEntityBuilder
        .create()
        .setContentType(
            ContentType.MULTIPART_FORM_DATA
                .withParameters(BasicNameValuePair("boundary", boundary))
        )
        .addBinaryBody("file", "ISA***...".byteInputStream())
        .build()

    val buff = ByteArrayOutputStream()
    entity.writeTo(buff)
    println(buff.toString())
}