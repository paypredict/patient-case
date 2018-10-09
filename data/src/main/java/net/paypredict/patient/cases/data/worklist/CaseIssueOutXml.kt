package net.paypredict.patient.cases.data.worklist

import com.mongodb.client.MongoCollection
import net.paypredict.patient.cases.PatientCases
import net.paypredict.patient.cases.data.CaseDataException
import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo._id
import net.paypredict.patient.cases.mongo.opt
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityCheckRes
import net.paypredict.patient.cases.pokitdok.eligibility.toEligibilityCheckRes
import org.bson.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import kotlin.coroutines.experimental.buildSequence
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.dom.DOMSource


/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 10/7/2018.
 */
val ordersDir: File by lazy { PatientCases.clientDir.resolve("orders") }

val srcDir: File by lazy {
    ordersDir.resolve("src").also {
        if (!it.isDirectory) throw CaseDataException("Orders source directory $it not found")
    }
}
val outDir: File by lazy {
    ordersDir.resolve("out").apply { mkdir() }
}

typealias DomDocument = org.w3c.dom.Document

private val documentBuilderFactory: DocumentBuilderFactory by lazy { DocumentBuilderFactory.newInstance() }
private val transformerFactory: TransformerFactory by lazy { TransformerFactory.newInstance() }

fun CaseIssue.createOutXml() {
    val case: Document = _id?.let { DBS.Collections.casesRaw().find(it._id()).firstOrNull() }
        ?: throw CaseDataException("document $_id not found in casesRaw")
    val fileName: String = case.opt("file", "name")
        ?: throw CaseDataException("file.name not found in casesRaw $_id")

    val srcFile = srcDir.resolve(fileName).also {
        if (!it.isFile) throw CaseDataException("Orders source XML file $it not found")
    }
    val outFile = outDir.resolve(fileName).also {
        if (it.exists()) throw CaseDataException("Orders out XML file $it already exists")
    }

    val dom: DomDocument = documentBuilderFactory.newDocumentBuilder()
        .parse(srcFile.toInputSource())

    val domSubscribersByResponsibilityCode = dom
        .getElementsByTagName("Subscriber")
        .toSequence()
        .filterIsInstance<Element>()
        .groupBy {
            val value = it.getAttribute("ResponsibilityCode")
            try {
                ResponsibilityOrder.valueOf(value)
            } catch (e: IllegalArgumentException) {
                throw CaseDataException("Invalid Subscriber/ResponsibilityCode ($value) in source XML file $fileName")
            }
        }
        .mapValues {
            if (it.value.size != 1)
                throw CaseDataException("Invalid Subscriber/ResponsibilityCode in source XML file $fileName")
            it.value.first()
        }

    val eligibilityCollection: MongoCollection<Document> by lazy { DBS.Collections.eligibility() }

    domSubscribersByResponsibilityCode.forEach { responsibility, out: Element ->
        val issue: IssueEligibility? = eligibility
            .lastOrNull { it.responsibility == responsibility.name && it.status?.ok == true }
        if (issue != null) {
            when (issue.status) {
                IssueEligibility.Status.Unchecked -> {
                    out.updateSaved(issue)
                }
                IssueEligibility.Status.Confirmed -> {
                    eligibilityCollection.toEligibilityCheckResPass(issue)?.result?.let { res ->
                        out.updateChecked(res) {
                            issue.subscriberRaw[it.aName]
                        }
                    }
                }
            }

        }
    }

    transformerFactory
        .newTransformer()
        .apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        }
        .transform(
            DOMSource(dom),
            outFile.toStreamResult()
        )
}

private fun Element.updateSaved(doc: IssueEligibility) {

}

private fun Element.updateChecked(doc: Document, def: (String) -> String?) =
    update(
        doc, def,
        "EffectiveDate" to "data.coverage.policy_effective_date",
        "PlanCode" to "data.coverage.insurance_type",
        "PayerId" to "data.payer.id",
        "GroupOrPlanNumber" to "data.coverage.group_number",
        "GroupOrPlanName" to "data.coverage.plan_description",
        "SubscriberPolicyNumber" to "data.subscriber.id",
        "OrganizationNameOrLastName" to "data.subscriber.last_name",
        "FirstName" to "data.subscriber.first_name",
        "MiddleInitial" to "data.subscriber.middle_name",
        "Zip" to "data.benefit_related_entities.address.zipcode",
        "Gender" to "data.subscriber.gender",
        "DOB" to "data.subscriber.birth_date",
        "Address1" to "data.benefit_related_entities.address.address_lines.[0]",
        "Address2" to "data.benefit_related_entities.address.address_lines.[1]",
        "City" to "data.benefit_related_entities.address.city",
        "State" to "data.benefit_related_entities.address.state",
        "SubscriberAddress1" to "data.subscriber.address.address_lines.[0]",
        "SubscriberAddress2" to "data.subscriber.address.address_lines.[1]",
        "SubscriberCity" to "data.subscriber.address.city",
        "SubscriberState" to "data.subscriber.address.state",
        "SubscriberZIP" to "data.subscriber.address.zipcode"
    )

private fun Element.update(doc: Document, def: (String) -> String?, vararg rules: Pair<String, String>) {
    for (rule in rules) {
        val keys = rule.second.split('.')
        val lastKey = keys.last()
        val value =
            (if (lastKey.endsWith("]")) {
                val index = lastKey.removeSurrounding("[", "]").toInt()
                doc.opt<List<*>>(*keys.dropLast(1).toTypedArray())?.getOrNull(index) as? String
            } else {
                doc.opt<String>(*keys.toTypedArray())
            })
                ?: def(rule.first)

        if (value != null)
            setAttribute(rule.first, value)
    }
}

private fun MongoCollection<Document>.toEligibilityCheckRes(issue: IssueEligibility): EligibilityCheckRes? =
    issue.eligibility?.let {
        find(it._id())
            .first()
            .toEligibilityCheckRes()
    }

private fun MongoCollection<Document>.toEligibilityCheckResPass(issue: IssueEligibility): EligibilityCheckRes.Pass? =
    toEligibilityCheckRes(issue) as? EligibilityCheckRes.Pass


private fun NodeList.toSequence(): Sequence<Node> = buildSequence {
    for (i in 0 until length) {
        yield(item(i))
    }
}

private const val XML_PREFIX = """<?xml version="1.0" encoding="utf-16" standalone="no"?>"""

private fun File.toInputSource(): InputSource =
    InputSource(readText().removePrefix(XML_PREFIX).reader())

private fun File.toStreamResult() =
    StreamResult(writer().also { it.write(XML_PREFIX) })

internal val String.aName: String
    get() = when {
        all { it.isUpperCase() } -> toLowerCase()
        else -> decapitalize()
    }

internal val String.eName: String
    get() = capitalize()
