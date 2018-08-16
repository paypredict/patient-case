package net.paypredict.patient.cases.imports

import com.mongodb.client.model.UpdateOptions
import net.paypredict.patient.cases.bson.*
import net.paypredict.patient.cases.data.DBS
import org.bson.Document
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import javax.xml.parsers.SAXParserFactory

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/16/2018.
 */
object XmlCaseImport {

    fun importFile(xmlFile: File): Document {
        val digest = xmlFile.digest()
        val case = xmlFile.inputStream().use { it.toDocument() }
        val casesRaw = DBS.Collections.casesRaw()
        val update = Document(
            `$set`, Document(
                mapOf(
                    "case" to case,
                    "file" to Document(
                        mapOf(
                            "name" to xmlFile.name,
                            "size" to xmlFile.length().toInt()
                        )
                    )
                )
            )
        )

        casesRaw.updateOne(
            Document("_id", digest), update,
            UpdateOptions().upsert(true)
        )
        return case
    }

    private class Box(
        val doc: Document
    )

    private fun InputStream.toDocument(): Document {
        val result = Document()
        SAXParserFactory.newInstance().newSAXParser().parse(this, object : DefaultHandler() {
            val path: MutableList<Box> = mutableListOf(Box(result))

            override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                val doc = Document()
                for (i in 0 until attributes.length) {
                    doc[attributes.getQName(i).aName] = attributes.getValue(i)
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
        "Case.SuperBillDetails"
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

    private val String.aName: String
        get() = decapitalize()

    private val String.eName: String
        get() = capitalize()

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

    private fun Array<String>.jsonOutOption(document: Document) {
        val option = "-json-out:"
        this.mapNotNull { if (it.startsWith(option)) it.removePrefix(option) else null }
            .lastOrNull()
            ?.let { File(it).writeText(document.toJson(JsonWriterSettings(JsonMode.STRICT, true))) }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val xmlFile = args.lastOrNull() ?: throw Error("Usage: XmlCaseImport [options] <xmlFile>")
        val document = importFile(File(xmlFile))
        args.jsonOutOption(document)
    }
}


