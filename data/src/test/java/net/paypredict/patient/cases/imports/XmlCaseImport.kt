package net.paypredict.patient.cases.imports

import com.mongodb.client.model.UpdateOptions
import de.svenjacobs.loremipsum.LoremIpsum
import net.paypredict.patient.cases.bson.`$set`
import net.paypredict.patient.cases.data.DBS
import net.paypredict.patient.cases.data.doc
import net.paypredict.patient.cases.data.invoke
import net.paypredict.patient.cases.data.worklist.*
import org.bson.Document
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.FileFilter
import java.io.Reader
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import javax.xml.parsers.SAXParserFactory

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/16/2018.
 */
object XmlCaseImport {

    data class Options(
        val removePrsData: Boolean = false,
        val addRndStatus: Boolean = false
    )


    fun importFile(xmlFile: File, options: Options = Options()): String {
        val digest = xmlFile.digest()
        val case = xmlFile.inputStream().reader().use { it.toDocument(options) }
        val casesRaw = DBS.Collections.casesRaw()
        val update = Document(
            `$set`, doc {
                doc["date"] = Date(xmlFile.lastModified())
                doc["case"] = case
                doc["file"] = doc {
                    doc["name"] = xmlFile.name
                    doc["size"] = xmlFile.length().toInt()
                }
                if (options.addRndStatus) {
                    doc["status"] = doc {
                        doc["checked"] = Date.from(LocalDateTime.now(ZoneOffset.UTC).toInstant(ZoneOffset.UTC))
                        doc["values"] = doc {
                            doc["npi"] = rndStatus()?.toDocument()
                            doc["eligibility"] = rndStatus()?.toDocument()
                            doc["address"] = rndStatus()?.toDocument()
                            doc["expert"] = rndStatus()?.toDocument()
                        }
                    }
                }
            }
        )

        casesRaw.updateOne(
            Document("_id", digest), update,
            UpdateOptions().upsert(true)
        )
        return digest
    }

    private val rnd: Random by lazy { Random() }

    private fun rndStatus() =
        when (rnd.nextInt(3)) {
            0 -> Status("AUTO_FIXED")
            1 -> Status("ERROR")
            else -> null
        }


    private class Box(
        val doc: Document
    )

    private fun Reader.toDocument(options: Options = Options()): Document {
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
                    val aValue = attributes.getValue(i)
                    val value = if (options.removePrsData)
                        when (aName) {
                            "subscriberPolicyNumber" -> "123123123123"
                            "address1" -> "Any Address"
                            "payerId" -> "12345"
                            "name" -> "AnyName"
                            "firstName" -> "AnyFirstName"
                            "lastName" -> "AnyLastName"
                            "organizationNameOrLastName" -> "AnyLastName"
                            else -> aValue
                        } else aValue
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
        "Case.SuperBillDetails.SuperBill",
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

    private val String.aName: String
        get() = when {
            all { it.isUpperCase() } -> toLowerCase()
            else -> decapitalize()
        }

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

    private fun Array<String>.optionValue(option: String) =
        this.mapNotNull { if (it.startsWith(option)) it.removePrefix(option) else null }
            .lastOrNull()


    private fun Array<String>.jsonOutOption(_id: String) {
        optionValue("--json-out:")
            ?.let { fileName ->
                val document = DBS.Collections.casesRaw().find(Document("_id", _id)).first()
                File(fileName).writeText(document.toJson(JsonWriterSettings(JsonMode.STRICT, true)))
            }
    }

    private fun Array<String>.testIssuesOption(_id: String) {
        if (contains("--test-issues")) {
            val case = DBS.Collections.casesRaw().find(Document("_id", _id)).first()
            fun eligibility(): List<Document> = mutableListOf<Document>().also { list ->
                case<List<*>>("case", "Case", "SubscriberDetails", "Subscriber")
                    ?.filterIsInstance<Document>()
                    ?.firstOrNull { it<String>("responsibilityCode") == "Primary" }
                    ?.let { primarySubscriber ->
                        list += IssueEligibility(
                            status = "Failed",
                            insurance = Insurance(
                                typeCode = primarySubscriber("insuranceTypeCode"),
                                payerId = primarySubscriber("payerId"),
                                planCode = primarySubscriber("planCode"),
                                payerName = primarySubscriber("payerName"),
                                zmPayerId = primarySubscriber("zmPayerId"),
                                zmPayerName = primarySubscriber("zmPayerName")
                            ),
                            subscriber = Subscriber(
                                firstName = primarySubscriber("firstName"),
                                lastName = primarySubscriber("organizationNameOrLastName"),
                                mi = primarySubscriber("middleInitial"),
                                gender = primarySubscriber("gender"),
                                dob = primarySubscriber("dob"),
                                groupName = primarySubscriber("groupOrPlanName"),
                                groupId = primarySubscriber("groupOrPlanNumber"),
                                relationshipCode = primarySubscriber("relationshipCode"),
                                policyNumber = primarySubscriber("subscriberPolicyNumber")
                            )
                        ).toDocument()
                    }
            }

            fun expert(): List<Document> =
                listOf(IssueExpert(text = LoremIpsum().words).toDocument())

            val update = Document(
                `$set`, Document(
                    mapOf(
                        "time" to Date(),
                        "patient" to case<Document>("case", "Case", "Patient")
                            ?.let { patient ->
                                Person(
                                    firstName = patient("firstName"),
                                    lastName = patient("lastName"),
                                    mi = patient("middleInitials"),
                                    dob = patient("dateOfBirth")
                                ).toDocument()
                            },
                        "issue" to Document(
                            mapOf(
                                "npi" to listOf(Document("status", "PASS").apply {
                                    val provider = case<Document>("case", "Case", "OrderingProvider", "Provider")
                                    this["npi"] = provider<String>("npi")
                                    this["firstName"] = provider<String>("firstName")
                                    this["lastName"] = provider<String>("lastName")

                                }),
                                "eligibility" to eligibility(),
                                "address" to listOf(Document("status", "PASS").apply {
                                    val patient = case<Document>("case", "Case", "Patient")
                                    this["address1"] = patient<String>("address1")
                                    this["address2"] = patient<String>("address2")
                                    this["zip"] = patient<String>("zip")
                                    this["city"] = patient<String>("city")
                                    this["state"] = patient<String>("state")
                                    this["person"] = Person(
                                        firstName = patient("firstName"),
                                        lastName = patient("lastName"),
                                        mi = patient("middleInitials"),
                                        dob = patient("dateOfBirth")
                                    ).toDocument()

                                }),
                                "expert" to expert()
                            )
                        )
                    )
                )
            )
            DBS.Collections.casesIssues().updateOne(
                Document("_id", _id), update,
                UpdateOptions().upsert(true)
            )
        }
    }

    private fun Array<String>.toOptions(): Options =
        Options(
            removePrsData = contains("--remove-prs"),
            addRndStatus = contains("--add-rnd-status")
        )


    @JvmStatic
    fun main(args: Array<String>) {
        val path = args.lastOrNull() ?: throw Error(
            """
            Usage: XmlCaseImport [--json-out:jsonFile] [--test-issues] [--dir] [--suffix:.xml] [--remove-prs] <path>
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
        files.forEach { xmlFile ->
            val _id = importFile(xmlFile, args.toOptions())
            println("${xmlFile.name}: $_id")
            args.jsonOutOption(_id)
            args.testIssuesOption(_id)
        }
    }
}
