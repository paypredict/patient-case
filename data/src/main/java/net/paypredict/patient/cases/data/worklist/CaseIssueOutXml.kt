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

    updateSubscribers(dom, fileName)

    outFile.writer().use { writer ->
        writer.write(XML_PREFIX)

        transformerFactory
            .newTransformer()
            .apply {
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            }
            .transform(
                DOMSource(dom),
                StreamResult(writer)
            )
    }
}

private fun CaseIssue.updateSubscribers(domDocument: DomDocument, fileName: String) {
    val domSubscribersByResponsibilityCode =
        domDocument
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
            .filter { it.responsibility == responsibility.name }
            .findPassed()
        if (issue != null) {
            when (issue.status) {
                IssueEligibility.Status.Unchecked -> {
                    out.updateSubscriber(issue)
                }
                IssueEligibility.Status.Confirmed -> {
                    val eligibilityRes =
                        eligibilityCollection.toEligibilityCheckResPass(issue)?.result
                    if (eligibilityRes != null)
                        out.updateSubscriber(eligibilityRes)
                }
            }
        }
    }


    if (domSubscribersByResponsibilityCode.isEmpty()) {
        val eligibilityList =
            eligibility.asSequence()
                .filter { it.responsibility != null }
                .groupBy { ResponsibilityOrder.valueOf(it.responsibility!!) }
                .mapValues { it.value.findPassed() }
                .values
                .filterNotNull()

        val domSubscriberDetails: Element =
            domDocument
                .getElementsByTagName("SubscriberDetails")
                .toSequence()
                .filterIsInstance<Element>()
                .firstOrNull()
                ?: domDocument.createElement("SubscriberDetails")
                    .also { domDocument.documentElement.firstChild.appendChild(it) }

        eligibilityList.forEach { issue ->
            val subscriber: Element = domDocument.createElement("Subscriber")
            subscriber[SubscriberAttr.RelationshipCode] = issue.responsibility
            subscriber.fillSubscriber(issue, eligibilityCollection)
            domSubscriberDetails.appendChild(subscriber)
        }
    }
}

private fun Element.fillSubscriber(
    issue: IssueEligibility,
    eligibilityCollection: MongoCollection<Document>
) {
    val eligibilityRes: Document? =
        if (issue.status is IssueEligibility.Status.Confirmed)
            eligibilityCollection.toEligibilityCheckResPass(issue)?.result else
            null

    if (eligibilityRes != null)
        updateSubscriber(eligibilityRes) else
        updateSubscriber(issue)


    SubscriberAttr.values()
        .filterNot { hasAttribute(it.name) }
        .forEach { this[it] = it.default }
}

private fun Element.updateSubscriber(issue: IssueEligibility) {
    issue.insurance?.let { insurance ->
        setIfNotNull(SubscriberAttr.PayerId, insurance.zmPayerId)
        setIfNotNull(SubscriberAttr.PayerName, insurance.zmPayerName)
    }
    issue.subscriber?.let { subscriber ->
        setIfNotNull(SubscriberAttr.RelationshipCode, subscriber.relationshipCode)
        setIfNotNull(SubscriberAttr.FirstName, subscriber.firstName)
        setIfNotNull(SubscriberAttr.MiddleInitial, subscriber.mi)
        setIfNotNull(SubscriberAttr.OrganizationNameOrLastName, subscriber.lastName)
        setIfNotNull(SubscriberAttr.Gender, subscriber.gender)
        setIfNotNull(SubscriberAttr.DOB, subscriber.dob)
        setIfNotNull(SubscriberAttr.SubscriberPolicyNumber, subscriber.policyNumber)
    }
}

private fun Element.setIfNotNull(attr: SubscriberAttr, value: String?) {
    if (value != null)
        setAttribute(attr.name, value)
}

private operator fun Element.set(attr: SubscriberAttr, value: String?) {
    setAttribute(attr.name, value ?: attr.default)
}

private fun Element.updateSubscriber(eligibilityRes: Document) {
    val rules = SubscriberAttr.values()
        .filter { it.eligibilityPath.isNotEmpty() }
        .map { it.name to it.eligibilityPath }
    update(eligibilityRes, rules)
    if (eligibilityRes.opt<Boolean>("data", "coverage", "active") == true)
        this[SubscriberAttr.IsElectronicPayer] = "Yes"
}

private fun Element.update(
    document: Document,
    rules: List<Pair<String, String>>
) {
    for (rule in rules) {
        val keys = rule.second.split('.')
        val lastKey = keys.last()
        val value =
            (if (lastKey.endsWith("]")) {
                val index = lastKey.removeSurrounding("[", "]").toInt()
                document.opt<List<*>>(*keys.dropLast(1).toTypedArray())?.getOrNull(index) as? String
            } else {
                document.opt<String>(*keys.toTypedArray())
            })

        if (value != null)
            setAttribute(rule.first, value)
    }
}

private enum class SubscriberAttr(val eligibilityPath: String = "", val default: String = "") {
    HolderEmp,
    EffectiveDate("data.coverage.policy_effective_date"),
    CarrierCode,
    PlanCode("data.coverage.insurance_type"),
    BillingPrecedence,
    InsuranceType,
    IsElectronicPayer,
    RelationshipCode,
    PayerId("data.payer.id"),
    GroupOrPlanNumber("data.coverage.group_number"),
    GroupOrPlanName("data.coverage.plan_description"),
    SubscriberPolicyNumber("data.subscriber.id"),
    OrganizationNameOrLastName("data.subscriber.last_name"),
    FirstName("data.subscriber.first_name"),
    MiddleInitial("data.subscriber.middle_name"),
    Zip("data.benefit_related_entities.address.zipcode"),
    Gender("data.subscriber.gender"),
    DOB("data.subscriber.birth_date"),
    Address1("data.benefit_related_entities.address.address_lines.[0]"),
    Address2("data.benefit_related_entities.address.address_lines.[1]"),
    City("data.benefit_related_entities.address.city"),
    State("data.benefit_related_entities.address.state"),
    InsuranceTypeCode,
    ResponsibilityCode,
    ClaimFilingIndicatorCode,
    PayerName("data.payer.name"),
    BillingType,
    SubscriberAddress1("data.subscriber.address.address_lines.[0]"),
    SubscriberAddress2("data.subscriber.address.address_lines.[1]"),
    SubscriberCity("data.subscriber.address.city"),
    SubscriberState("data.subscriber.address.state"),
    SubscriberZIP("data.subscriber.address.zipcode")
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

internal val String.aName: String
    get() = when {
        all { it.isUpperCase() } -> toLowerCase()
        else -> decapitalize()
    }

internal val String.eName: String
    get() = capitalize()
