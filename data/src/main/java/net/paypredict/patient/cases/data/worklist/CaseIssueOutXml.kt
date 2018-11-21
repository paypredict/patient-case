package net.paypredict.patient.cases.data.worklist

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import net.paypredict.patient.cases.PatientCases
import net.paypredict.patient.cases.data.CaseDataException
import net.paypredict.patient.cases.Import
import net.paypredict.patient.cases.digest
import net.paypredict.patient.cases.mongo.*
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityCheckRes
import net.paypredict.patient.cases.pokitdok.eligibility.toEligibilityCheckRes
import org.bson.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.File
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.streams.toList

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 10/7/2018.
 */
val ordersDir: File by lazy { PatientCases.clientDir.resolve("orders") }

val ordersSrcDir: File by lazy {
    ordersDir.resolve("src").also {
        if (!it.isDirectory) throw CaseDataException("Orders source directory $it not found")
    }
}
val ordersArchiveDir: File by lazy {
    ordersDir.resolve("archive").apply { mkdir() }
}
val ordersOutDir: File by lazy {
    ordersDir.resolve("out").apply { mkdir() }
}

fun ordersArchiveFile(digest: String): File =
    ordersArchiveDir
        .resolve(digest.take(4)).apply { mkdir() }
        .resolve(digest)

fun File.archive(digest: String = digest()): String {
    val archiveFile = ordersArchiveFile(digest)
    if (!archiveFile.exists())
        copyTo(archiveFile)
    return digest
}

typealias DomDocument = org.w3c.dom.Document

private val documentBuilderFactory: DocumentBuilderFactory by lazy { DocumentBuilderFactory.newInstance() }
private val transformerFactory: TransformerFactory by lazy { TransformerFactory.newInstance() }

fun CaseHist.createOutXml(
    cases: DocumentMongoCollection = DBS.Collections.cases(),
    casesOut: DocumentMongoCollection = DBS.Collections.casesOut()
): String? {
    val caseFilter: Document = _id._id()
    val case: Document = cases.find(caseFilter).firstOrNull()
        ?: throw CaseDataException("document $_id not found in collection cases")
    val fileName: String = case.opt("file", "name")
        ?: throw CaseDataException("file.name not found in collection cases $_id")

    val srcFile = ordersArchiveFile(_id).also {
        if (!it.isFile) throw CaseDataException("Orders Archive File $it not found")
    }
    val outFile = ordersOutDir.resolve(fileName).also {
        if (it.exists() && !it.delete()) throw CaseDataException("Orders out XML file $it already exists")
    }

    val domDocument: DomDocument =
        documentBuilderFactory
            .newDocumentBuilder()
            .parse(srcFile.toInputSource())

    updateSubscribers(domDocument, fileName)

    updatePatient(domDocument)

    updateProvider(domDocument)

    outFile.writer().use { writer ->
        writer.write(XML_PREFIX)

        transformerFactory
            .newTransformer()
            .apply {
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            }
            .transform(
                DOMSource(domDocument),
                StreamResult(writer)
            )
    }

    val outFileDigest: String? =
        Import.importXmlFile(
            xmlFile = outFile,
            cases = casesOut,
            skipByNameAndTime = false,
            override = false,
            onUpsertDoc = { self["ref.src"] = _id },
            onNewFile = { outFile.archive(it) }
        )

    outFileDigest
        ?.also {
            cases.upsertOne(caseFilter, doc { self[`$set`] = doc { self["ref.out"] = it } })
        }


    makeFixedCopies(srcFile, outFile, fileName)
    makeTestCopy(fileName)

    return outFileDigest
}

private val fixedSrcDir: File by lazy {
    ordersDir.resolve("src-fixed").apply { mkdir() }
}
private val fixedOutDir: File by lazy {
    ordersDir.resolve("out-fixed").apply { mkdir() }
}

private fun makeFixedCopies(srcFile: File, outFile: File, fileName: String) {
    srcFile.makeFixedCopy(fixedSrcDir.resolve(fileName))
    outFile.makeFixedCopy(fixedOutDir.resolve(fileName))
}

private fun File.makeFixedCopy(dst: File) =
    dst.writeText(readText().removePrefix(XML_PREFIX))

private val testOutDir: File by lazy {
    ordersDir.resolve("out-test").apply { mkdir() }
}

private fun makeTestCopy(fileName: String) {
    val domDocument: DomDocument = documentBuilderFactory.newDocumentBuilder()
        .parse(fixedOutDir.resolve(fileName))

    val randomCaseGUID: String = UUID.randomUUID().toString().toUpperCase()

    fun nextInt(name: String): Int =
        DBS.Collections.settings()
            .findOneAndUpdate(
                name._id(),
                doc { self[`$inc`] = doc { self["value"] = 1 } },
                FindOneAndUpdateOptions()
                    .upsert(true)
                    .returnDocument(ReturnDocument.AFTER)
            )
            ?.opt<Int>("value")
            ?: throw AssertionError("nextInt($name) failed")

    (domDocument.firstChild as Element).also { element ->
        val prefix = element
            .getAttribute("AccessionNumber")
            .substringBefore('-')
        val next = nextInt("test_nextAccessionNumber")
            .toString()
            .padStart(4, '0')
        element.setAttribute("AccessionNumber", "$prefix-pp$next")
    }

    domDocument.getElementsByTagName("*")
        .toSequence()
        .filterIsInstance<Element>()
        .forEach {
            if (it.hasAttribute("CaseGUID")) {
                it.setAttribute("CaseGUID", randomCaseGUID)
            }
        }

    val nextFirstName: String =
        "Bill" + nextInt("text_nextName")
            .toString().chars().toList().joinToString(separator = "") { (it + 16).toChar().toString() }
    val nextLastName = "DONOT"
    val nextName = "$nextFirstName $nextLastName"

    domDocument.getElementsByTagName("Patient")
        .toSequence()
        .filterIsInstance<Element>()
        .forEach {
            it.setIfNotNullOrBlank(PatientAttr.Name, nextName)
            it.setIfNotNullOrBlank(PatientAttr.FirstName, nextFirstName)
            it.setIfNotNullOrBlank(PatientAttr.LastName, nextLastName)
            it.setIfNotNullOrBlank(PatientAttr.MiddleInitials, "")

            it.setIfNotNullOrBlank(PatientAttr.GuarantorName, nextName)
            it.setIfNotNullOrBlank(PatientAttr.GuarantorFirstName, nextFirstName)
            it.setIfNotNullOrBlank(PatientAttr.GuarantorLastName, nextLastName)
            it.setIfNotNullOrBlank(PatientAttr.GuarantorMiddleInitials, "")
        }

    domDocument.getElementsByTagName("Subscriber")
        .toSequence()
        .filterIsInstance<Element>()
        .forEach {
            it.setIfNotNullOrBlank(SubscriberAttr.FirstName, nextFirstName)
            it.setIfNotNullOrBlank(SubscriberAttr.OrganizationNameOrLastName, nextLastName)
            it.setIfNotNullOrBlank(SubscriberAttr.MiddleInitial, "")
        }

    testOutDir.resolve(fileName).writer().use { writer ->
        writer.write(XML_PREFIX)

        transformerFactory
            .newTransformer()
            .apply {
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            }
            .transform(
                DOMSource(domDocument),
                StreamResult(writer)
            )
    }
}

private fun CaseHist.updateSubscribers(domDocument: DomDocument, fileName: String) {
    val caseElement =
        domDocument
            .getElementsByTagName("Case")
            .toSequence()
            .filterIsInstance<Element>()
            .firstOrNull()
            ?: throw CaseDataException("Element <Case> not found in source XML file $fileName")

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

    val caseStatus = status ?: CaseStatus()
    domSubscribersByResponsibilityCode.forEach { responsibility, out: Element ->
        val issue: IssueEligibility? = eligibility
            .filter { it.responsibility == responsibility.name }
            .findBest(caseStatus)
        if (issue != null) {
            out.updateSubscriber(eligibilityCollection, issue)
            out.updateSubscriber(issue)
        }
    }


    if (domSubscribersByResponsibilityCode.isEmpty()) {
        val eligibilityList =
            eligibility.asSequence()
                .filter { it.responsibility != null }
                .groupBy { ResponsibilityOrder.valueOf(it.responsibility!!) }
                .mapValues { it.value.findBest(caseStatus) }
                .values
                .filterNotNull()

        val domSubscriberDetails: Element =
            domDocument
                .getElementsByTagName("SubscriberDetails")
                .toSequence()
                .filterIsInstance<Element>()
                .firstOrNull()
                ?: domDocument.createElement("SubscriberDetails")
                    .also { caseElement.appendChild(it) }

        eligibilityList.forEach { issue ->
            val subscriber: Element = domDocument.createElement("Subscriber")
            subscriber[SubscriberAttr.RelationshipCode] = issue.responsibility
            subscriber.fillNewSubscriber(eligibilityCollection, issue)
            domSubscriberDetails.appendChild(subscriber)
        }
    }
}


private fun Element.fillNewSubscriber(
    eligibilityCollection: MongoCollection<Document>,
    issue: IssueEligibility
) {
    updateSubscriber(eligibilityCollection, issue)
    updateSubscriber(issue)
    SubscriberAttr.values()
        .filterNot { hasAttribute(it.name) }
        .forEach { this[it] = it.default }
}

private fun Element.updateSubscriber(
    eligibility: MongoCollection<Document>,
    issue: IssueEligibility
) {
    fun applyEligibilityRes(eligibilityRes: Document) {

        fun updateElement(document: Document, rules: List<Pair<String, String>>) {
            for ((attrName, docPath) in rules) {
                val keys = docPath.split('.')
                val lastKey = keys.last()
                val value =
                    (if (lastKey.endsWith("]")) {
                        val index = lastKey.removeSurrounding("[", "]").toInt()
                        document.opt<List<*>>(*keys.dropLast(1).toTypedArray())?.getOrNull(index) as? String
                    } else {
                        document.opt<String>(*keys.toTypedArray())
                    })

                if (value != null)
                    setAttribute(attrName, value)
            }
        }


        val rules = SubscriberAttr.values()
            .filter { it.eligibilityPath.isNotEmpty() }
            .map { it.name to it.eligibilityPath }

        updateElement(eligibilityRes, rules)
        if (eligibilityRes.opt<Boolean>("data", "coverage", "active") == true)
            this[SubscriberAttr.IsElectronicPayer] = "Yes"
    }

    eligibility.toEligibilityCheckResPass(issue)
        ?.result
        ?.also { applyEligibilityRes(it) }
}

private fun Element.updateSubscriber(issue: IssueEligibility) {
    issue.insurance?.let { insurance ->
        setIfNotNullOrBlank(SubscriberAttr.PayerId, insurance.zmPayerId)
        setIfNotNullOrBlank(SubscriberAttr.PayerName, insurance.zmPayerName)
    }
    issue.subscriber?.let { subscriber ->
        setIfNotNullOrBlank(SubscriberAttr.RelationshipCode, subscriber.relationshipCode)
        setIfNotNullOrBlank(SubscriberAttr.FirstName, subscriber.firstName)
        setIfNotNullOrBlank(SubscriberAttr.MiddleInitial, subscriber.mi)
        setIfNotNullOrBlank(SubscriberAttr.OrganizationNameOrLastName, subscriber.lastName)
        setIfNotNullOrBlank(SubscriberAttr.Gender, subscriber.gender)
        setIfNotNullOrBlank(SubscriberAttr.DOB, subscriber.dob)
        setIfNotNullOrBlank(SubscriberAttr.SubscriberPolicyNumber, subscriber.policyNumber)
    }
}

private fun Element.setIfNotNullOrBlank(attr: SubscriberAttr, value: String?) {
    if (!value.isNullOrBlank())
        setAttribute(attr.name, value)
}

private operator fun Element.set(attr: SubscriberAttr, value: String?) {
    setAttribute(attr.name, value ?: attr.default)
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
    PayerId,
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

private fun CaseHist.updateProvider(domDocument: DomDocument) {
    val element: Element =
        domDocument
            .getElementsByTagName("Provider")
            .toSequence()
            .filter { (it.parentNode as? Element)?.tagName == "OrderingProvider" }
            .firstOrNull() as? Element
            ?: return

    val issue: IssueNPI = npi.findPassed() ?: return

    element.setIfNotNullOrBlank(ProviderAttr.FirstName, issue.name?.firstName)
    element.setIfNotNullOrBlank(ProviderAttr.MiddleInitials, issue.name?.mi)
    element.setIfNotNullOrBlank(ProviderAttr.LastName, issue.name?.lastName)

    ProviderAttr.values().forEach { attr ->
        if (!element.hasAttribute(attr.name)) element[attr] = attr.default
    }
}

private enum class ProviderAttr(val default: String = "") {
    Address1,
    Address2,
    City,
    State,
    Zip,
    DisplayName,
    Title,
    FirstName,
    MiddleInitials,
    LastName,
    NPI,
    Phone,
    RoleName,
    UPIN,
}

private fun Element.setIfNotNullOrBlank(attr: ProviderAttr, value: String?) {
    if (!value.isNullOrBlank())
        setAttribute(attr.name, value)
}

private operator fun Element.set(attr: ProviderAttr, value: String?) {
    setAttribute(attr.name, value ?: attr.default)
}


private fun CaseHist.updatePatient(domDocument: DomDocument) {
    val domPatient: Element =
        domDocument
            .getElementsByTagName("Patient")
            .toSequence()
            .firstOrNull() as? Element
            ?: return

    val issue: IssueAddress = address.findPassed() ?: return

    domPatient.setIfNotNullOrBlank(PatientAttr.Address1, issue.address1)
    domPatient.setIfNotNullOrBlank(PatientAttr.Address2, issue.address2)
    domPatient.setIfNotNullOrBlank(PatientAttr.Zip, issue.zip)
    domPatient.setIfNotNullOrBlank(PatientAttr.City, issue.city)
    domPatient.setIfNotNullOrBlank(PatientAttr.State, issue.state)

    PatientAttr.values().forEach { attr ->
        if (!domPatient.hasAttribute(attr.name)) domPatient[attr] = attr.default
    }
}

private enum class PatientAttr(val default: String = "") {
    Address1,
    Address2,
    City,
    DateOfBirth,
    FirstName,
    Gender,
    GuarantorAddress1,
    GuarantorAddress2,
    GuarantorCity,
    GuarantorFirstName,
    GuarantorGender,
    GuarantorHomePhone,
    GuarantorLastName,
    GuarantorMedicalRecNo,
    GuarantorMiddleInitials,
    GuarantorName,
    GuarantorRelationship,
    GuarantorSSN,
    GuarantorState,
    GuarantorWorkPhone,
    GuarantorZip,
    HomePhone,
    LastName,
    MedicalRecNo,
    MiddleInitials,
    Name,
    PMS,
    SSN,
    State,
    WorkPhone,
    Zip,
}

private fun Element.setIfNotNullOrBlank(attr: PatientAttr, value: String?) {
    if (!value.isNullOrBlank())
        setAttribute(attr.name, value)
}

private operator fun Element.set(attr: PatientAttr, value: String?) {
    setAttribute(attr.name, value ?: attr.default)
}


private fun MongoCollection<Document>.toEligibilityCheckRes(issue: IssueEligibility): EligibilityCheckRes? =
    issue.eligibility?.let {
        find(it._id())
            .first()
            .toEligibilityCheckRes()
    }

private fun MongoCollection<Document>.toEligibilityCheckResPass(issue: IssueEligibility): EligibilityCheckRes.Pass? =
    toEligibilityCheckRes(issue) as? EligibilityCheckRes.Pass


private fun NodeList.toSequence(): Sequence<Node> = sequence {
    for (i in 0 until length) {
        yield(item(i))
    }
}

private const val XML_PREFIX = """<?xml version="1.0" encoding="utf-16" standalone="no"?>"""

private fun File.toInputSource(): InputSource =
    InputSource(readText().removePrefix(XML_PREFIX).reader())
