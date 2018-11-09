package net.paypredict.patient.cases.data.cases

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.CountOptions
import net.paypredict.patient.cases.data.worklist.aName
import net.paypredict.patient.cases.data.worklist.eName
import net.paypredict.patient.cases.mongo.*
import org.bson.Document
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.FileFilter
import java.io.Reader
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.*
import javax.xml.parsers.SAXParserFactory
import kotlin.collections.set

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 11/1/2018.
 */

object Import {

    private val limitOne = CountOptions().limit(1)
    private val casesFileFilter: (name: String, created: Date) -> Document by lazy {
        DBS.Collections.cases().apply {
            createIndex(doc {
                doc["file.name"] = 1
                doc["file.created"] = 1
            })
        }
        fun(name: String, created: Date): Document =
            doc {
                doc[`$and`] = listOf(
                    doc { doc["file.name"] = name },
                    doc { doc["file.created"] = created }
                )
            }
    }

    fun importXmlFile(
        xmlFile: File,
        cases: MongoCollection<Document> = DBS.Collections.cases(),
        skipByNameAndTime: Boolean = false,
        override: Boolean = true,
        onNewFile: (digest: String) -> Unit = {}
    ): String? {
        val created = Date(xmlFile.created())
        if (skipByNameAndTime) {
            val filter = casesFileFilter(xmlFile.name, created)
            if (cases.count(filter, limitOne) > 0) return null
        }

        val digest = xmlFile.digest()
        val filter = digest._id()
        if (cases.count(filter, limitOne) == 0L) {
            onNewFile(digest)
        } else {
            if (!override) return null
        }

        val case = xmlFile.reader().use { it.toDocument() }
        val update = doc {
            doc[`$set`] = doc {
                doc["case"] = case
                doc["file"] = doc {
                    doc["name"] = xmlFile.name
                    doc["size"] = xmlFile.length().toInt()
                    doc["created"] = created
                }
            }
        }

        cases.upsertOne(filter, update) {
            cases.updateOne(it, doc { doc[`$set`] = doc { doc["doc.created"] = Date() } })
        }

        return digest
    }

    private class Box(
        val doc: Document
    )

    private fun Reader.toDocument(): Document {
        val result = Document()
        val inputSource = InputSource(readText()
            .let { text ->
                val err = """<?xml version="1.0" encoding="utf-16" standalone="no"?>"""
                when {
                    text.startsWith(err) -> text.removePrefix(err)
                    else -> text
                }
            }
            .reader()
        )
        SAXParserFactory.newInstance().newSAXParser().parse(inputSource, object : DefaultHandler() {
            val path: MutableList<Box> = mutableListOf(
                Box(result)
            )

            override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                val doc = Document()
                for (i in 0 until attributes.length) {
                    val aName = attributes.getQName(i).aName
                    val value = attributes.getValue(i)
                    doc[aName] = value
                }
                val box = Box(doc)
                val parent = path.last()
                parent.doc[qName.eName] = (parent.doc[qName.eName] as? List<*> ?: emptyList<Any>()) + doc
                path += box
            }

            override fun endElement(uri: String, localName: String, qName: String) {
                path -= path.last()
            }
        })
        result.foldArrays()
        return result
    }

    private val foldablePathSet = setOf(
        "Case",
        "Case.Patient",
        "Case.ReportDetails",
        "Case.ReportDetails.Report",
        "Case.ReportDetails.Report.File",
        "Case.Attachments",
        "Case.ServiceProvider.Location",
        "Case.OrderingProvider",
        "Case.OrderingProvider.Provider",
        "Case.OrderingProvider.Location",
        "Case.ReportingProvider.Provider",
        "Case.ReportingProvider.Location",
        "Case.SubscriberDetails",
        "Case.OrderDetails",
        "Case.OrderDetails.Order",
        "Case.OrderDetails.ObservationDetails",
        "Case.ResultDetails",
        "Case.ResultDetails.Result",
        "Case.ReportingProvider",
        "Case.ServiceProvider",
        "Case.SuperBillDetails",
        "Case.SuperBillDetails.ServiceProvider.Location",
        "Case.SuperBillDetails.RenderingProvider.Provider",
        "Case.SuperBillDetails.RenderingProvider.Location"
    )

    private val Collection<String>.isPathFoldable: Boolean
        get() = joinToString(separator = ".") in foldablePathSet

    private fun Document.foldArrays(path: List<String> = emptyList()) {
        val folding = keys.mapNotNull { key ->
            val value = this[key]
            if (value is List<*> && value.size == 1 && (path + key).isPathFoldable)
                key to value.first() else
                null
        }
        keys.forEach { key ->
            val value = this[key]
            when (value) {
                is Document -> value.foldArrays(path + key)
                is List<*> -> value.filterIsInstance<Document>().forEach { doc ->
                    doc.foldArrays(path + key)
                }
            }
        }
        folding.forEach { (key, value) ->
            this[key] = value
        }
    }

    private fun File.digest(): String {
        val digest = MessageDigest.getInstance("SHA")
        inputStream().use { inputStream ->
            val bytes = ByteArray(4096)
            while (true) {
                val res = inputStream.read(bytes)
                if (res == -1) break
                digest.update(bytes, 0, res)
            }
        }
        val bytes = digest.digest()
        return bytes.joinToString(separator = "") {
            (it.toInt() and 0xff)
                .toString(16)
                .padStart(2, '0')
        }
    }

    private fun Array<String>.optionValue(option: String) =
        this.mapNotNull { if (it.startsWith(option)) it.removePrefix(option) else null }
            .lastOrNull()

    private fun Array<String>.minTime(): Long {
        val prefix = "--days:"
        val arg = firstOrNull { it.startsWith(prefix) } ?: return 0
        val days = arg.removePrefix(prefix).toIntOrNull() ?: return 0
        return System.currentTimeMillis() - 1000L * 60 * 60 * 24 * days
    }


    @JvmStatic
    fun main(args: Array<String>) {
        val path = args.lastOrNull() ?: throw Error(
            """
            Usage: Import [--dir] [--suffix:.xml] <path>
            """
        )

        val files: List<File> = File(path).let { file ->
            if (args.contains("--dir")) {
                val suffix = args.optionValue("--suffix:") ?: ".xml"
                file.listFiles(FileFilter {
                    it.isFile && it.name.endsWith(suffix, ignoreCase = true)
                })?.toList() ?: emptyList()
            } else
                listOf(file)
        }

        val minTime = args.minTime()
        println("minTime: [${Date(minTime)}]")
        for (xmlFile in files) {
            val modified = xmlFile.lastModified()
            print("${xmlFile.name} [${Date(modified)}]")
            if (modified < minTime) {
                println(" < minTime")
                continue
            }

            val _id = importXmlFile(xmlFile)
            println(" $_id")
        }
    }
}

fun File.created(): Long =
    Files
        .readAttributes<BasicFileAttributes>(toPath(), BasicFileAttributes::class.java)
        .creationTime()
        .toMillis()