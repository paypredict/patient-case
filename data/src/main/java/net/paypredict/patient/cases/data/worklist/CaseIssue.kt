package net.paypredict.patient.cases.data.worklist

import com.vaadin.flow.templatemodel.Encode
import net.paypredict.patient.cases.DataView
import net.paypredict.patient.cases.MetaData
import net.paypredict.patient.cases.VaadinBean
import net.paypredict.patient.cases.data.DateToDateTimeBeanEncoder
import net.paypredict.patient.cases.data.doc
import net.paypredict.patient.cases.data.opt
import net.paypredict.patient.cases.metaDataMap
import org.bson.Document
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/19/2018.
 */

@VaadinBean
data class CaseIssue(
    @DataView("_id", isVisible = false)
    val _id: String? = null,

    @set:Encode(DateToDateTimeBeanEncoder::class)
    @DataView("Date.Time", order = 10)
    var time: Date? = null,

    @DataView("Patient", order = 20, isVisible = false)
    var patient: Person? = null,

    @DataView("NPI", order = 30)
    var npi: List<IssueNPI> = emptyList(),

    @DataView("Eligibility", order = 40)
    var eligibility: List<IssueEligibility> = emptyList(),

    @DataView("Address", order = 50)
    var address: List<IssueAddress> = emptyList(),

    @DataView("Expert", order = 60)
    var expert: List<IssueExpert> = emptyList()
)

interface IssuesStatus {
    var status: String?
}

interface IssuesClass<T : IssuesStatus> {
    val caption: String
    val beanType: Class<T>
    val metaData: Map<String, MetaData<T>>
}

@VaadinBean
data class IssueNPI(
    @DataView("Status", flexGrow = 1, order = 10)
    override var status: String? = null,

    @DataView("NPI", flexGrow = 1, order = 20)
    var npi: String? = null,

    @DataView("Name", flexGrow = 3, order = 30)
    var name: Person? = null,

    @DataView("Taxonomies", order = 200, isVisible = false)
    var taxonomies: List<Taxonomy> = emptyList(),

    @DataView("Original", order = 201, isVisible = false)
    var original: IssueNPI? = null,

    @DataView("Error", order = 202, isVisible = false)
    var error: String? = null

) : IssuesStatus {

    @DataView("Taxonomy", flexGrow = 3, order = 40)
    val primaryTaxonomy: Taxonomy?
        get() = taxonomies.firstOrNull { it.primary == true }

    @VaadinBean
    data class Taxonomy(
        val primary: Boolean? = null,
        val code: String? = null,
        val license: String? = null,
        val desc: String? = null,
        val state: String? = null
    )

    companion object : IssuesClass<IssueNPI> {
        override val caption = "Referring Provider"
        override val beanType = IssueNPI::class.java
        override val metaData = metaDataMap<IssueNPI>()
    }
}

fun IssueNPI.nameEquals(other: IssueNPI, ignoreCase: Boolean = true, compareMI: Boolean = true): Boolean {
    if (!(name?.firstName ?: "").equals(other.name?.firstName ?: "", ignoreCase = ignoreCase)) return false
    if (!(name?.lastName ?: "").equals(other.name?.lastName ?: "", ignoreCase = ignoreCase)) return false
    if (compareMI && !(name?.mi ?: "").equals(other.name?.mi ?: "", ignoreCase = ignoreCase)) return false
    return true
}


@VaadinBean
data class IssueEligibility(
    @DataView("Status", order = 10, flexGrow = 1)
    override var status: String? = null,

    @DataView("Insurance", order = 20, flexGrow = 5)
    var insurance: Insurance? = null,

    @DataView("Subscriber", order = 30, flexGrow = 2)
    var subscriber: Subscriber? = null,

    @DataView("eligibility._id", order = 40, isVisible = false)
    var eligibility: String? = null

) : IssuesStatus {
    companion object : IssuesClass<IssueEligibility> {
        override val caption = "Patient Eligibility"
        override val beanType = IssueEligibility::class.java
        override val metaData = metaDataMap<IssueEligibility>()
    }
}

/**
 * `Case.SubscriberDetails.Subscriber`
 *  * responsibilityCode == Primary
 */
@VaadinBean
data class Insurance(
    /** `Case.SubscriberDetails.Subscriber.insuranceTypeCode` */
    @DataView("Type")
    var typeCode: String? = null,

    /** `Case.SubscriberDetails.Subscriber.payerId` */
    @DataView("Payer ID")
    var payerId: String? = null,

    /** `Case.SubscriberDetails.Subscriber.planCode` */
    @DataView("Plan Code")
    var planCode: String? = null,

    /** `Case.SubscriberDetails.Subscriber.payerName` */
    @DataView("Payer Name")
    var payerName: String? = null,

    @DataView("ZirMed Payer ID")
    var zmPayerId: String? = null,

    @DataView("ZirMed Payer Name")
    var zmPayerName: String? = null
)

/**
 * `Case.SubscriberDetails.Subscriber`
 *  * responsibilityCode == Primary
 */
@VaadinBean
data class Subscriber(
    /** `Case.SubscriberDetails.Subscriber.firstName` */
    @DataView("First Name")
    var firstName: String? = null,

    /** `Case.SubscriberDetails.Subscriber.organizationNameOrLastName` */
    @DataView("Last Name")
    var lastName: String? = null,

    /** `Case.SubscriberDetails.Subscriber.middleInitial` */
    @DataView("MI")
    var mi: String? = null,

    /** `Case.SubscriberDetails.Subscriber.gender` */
    @DataView("Gender")
    var gender: String? = null,

    /** `Case.SubscriberDetails.Subscriber.dob` */
    @DataView("DOB")
    var dob: String? = null,

    /** `Case.SubscriberDetails.Subscriber.groupOrPlanName` */
    @DataView("Group Name")
    var groupName: String? = null,

    /** `Case.SubscriberDetails.Subscriber.groupOrPlanNumber` */
    @DataView("Group ID")
    var groupId: String? = null,

    /** `Case.SubscriberDetails.Subscriber.relationshipCode` */
    @DataView("Relationship Code")
    var relationshipCode: String? = null,

    /** `Case.SubscriberDetails.Subscriber.subscriberPolicyNumber` */
    @DataView("Policy Number")
    var policyNumber: String? = null
) {
    companion object {
        val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    }

    val dobAsLocalDate: LocalDate?
        get() = dob?.let { LocalDate.parse(it, dateFormat) }
}

/**
 * `Case.SubscriberDetails.Patient`
 */
@VaadinBean
data class Person(
    @DataView("First Name")
    val firstName: String? = null,

    @DataView("Last Name")
    val lastName: String? = null,

    @DataView("MI")
    val mi: String? = null,

    @DataView("Gender")
    val gender: String? = null,

    @DataView("DOB")
    val dob: String? = null
) {
    companion object {
        val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    }

    val dobAsLocalDate: LocalDate?
        get() = dob?.let { LocalDate.parse(it, dateFormat) }
}

infix fun LocalDate.formatAs(dateFormat: DateTimeFormatter): String =
    dateFormat.format(this)


@VaadinBean
data class IssueAddress(
    @DataView("Status", order = 10)
    override var status: String? = null,

    @DataView("Address 1", order = 20)
    var address1: String? = null,

    @DataView("Address 2", order = 30)
    var address2: String? = null,

    @DataView("ZIP", order = 40)
    var zip: String? = null,

    @DataView("City", order = 50)
    var city: String? = null,

    @DataView("State", order = 60)
    var state: String? = null,

    @DataView("Person", order = 70, isVisible = false)
    var person: Person? = null

) : IssuesStatus {
    companion object : IssuesClass<IssueAddress> {
        override val caption = "Patient Address"
        override val beanType = IssueAddress::class.java
        override val metaData = metaDataMap<IssueAddress>()
    }
}

@VaadinBean
data class IssueExpert(
    @DataView("Status", order = 10)
    override var status: String? = null,

    @DataView("Subject", order = 20)
    var subject: String? = null,

    @DataView("Text", order = 30)
    var text: String? = null

) : IssuesStatus {
    companion object : IssuesClass<IssueExpert> {
        override val caption = "Expert Advice"
        override val beanType = IssueExpert::class.java
        override val metaData = metaDataMap<IssueExpert>()
    }
}

fun Document.toCaseIssue(): CaseIssue =
    CaseIssue(
        _id = get("_id").toString(),
        time = opt<Date>("time"),
        patient = opt<Document>("patient")?.toPerson(),
        npi = opt<List<*>>("issue", "npi")
            ?.asSequence()
            ?.filterIsInstance<Document>()
            ?.map { it.toIssueNPI() }
            ?.toList()
            ?: emptyList(),
        eligibility = opt<List<*>>("issue", "eligibility")
            ?.asSequence()
            ?.filterIsInstance<Document>()
            ?.map { it.toIssueEligibility() }
            ?.toList()
            ?: emptyList(),
        address = opt<List<*>>("issue", "address")
            ?.asSequence()
            ?.filterIsInstance<Document>()
            ?.map { it.toIssueAddress() }
            ?.toList()
            ?: emptyList(),
        expert = opt<List<*>>("issue", "expert")
            ?.asSequence()
            ?.filterIsInstance<Document>()
            ?.map { it.toIssueExpert() }
            ?.toList()
            ?: emptyList()
    )

fun CaseIssue.toDocument(): Document = doc {
    doc["_id"] = _id
    doc["time"] = time
    doc["patient"] = patient?.toDocument()
    doc["issue"] = doc {
        doc["npi"] = npi.map { it.toDocument() }
        doc["eligibility"] = eligibility.map { it.toDocument() }
        doc["address"] = address.map { it.toDocument() }
        doc["expert"] = expert.map { it.toDocument() }
    }
}

fun Document.toIssueNPI(): IssueNPI =
    IssueNPI(
        status = opt("status"),
        npi = opt("npi"),
        name = opt<Document>("name")?.toPerson(),
        original = opt<Document>("original")?.toIssueNPI(),
        taxonomies = opt<List<*>>("taxonomies")
            ?.asSequence()
            ?.filterIsInstance<Document>()
            ?.map { it.toTaxonomy() }
            ?.toList()
            ?: emptyList(),
        error = opt("error")
    )

fun IssueNPI.toDocument(): Document = doc {
    opt("status", status)
    doc["npi"] = npi
    doc["name"] = name?.toDocument()
    opt("original", original?.toDocument())
    opt("taxonomies", taxonomies.map { it.toDocument() })
    opt("error", error)
}


fun Document.toTaxonomy(): IssueNPI.Taxonomy =
    IssueNPI.Taxonomy(
        primary = opt("primary"),
        code = opt("code"),
        license = opt("license"),
        desc = opt("desc"),
        state = opt("state")
    )

fun IssueNPI.Taxonomy.toDocument(): Document = doc {
    opt("primary", primary)
    opt("code", code)
    opt("license", license)
    opt("desc", desc)
    opt("state", state)
}


private fun Document.toIssueEligibility(): IssueEligibility =
    IssueEligibility(
        status = opt("status"),
        insurance = opt<Document>("insurance")?.toInsurance(),
        subscriber = opt<Document>("subscriber")?.toSubscriber(),
        eligibility = opt<String>("eligibility")
    )

fun IssueEligibility.toDocument(): Document = doc {
    doc["status"] = status
    doc["insurance"] = insurance?.toDocument()
    doc["subscriber"] = subscriber?.toDocument()
    doc["eligibility"] = eligibility
}

private fun Insurance.toDocument(): Document = doc {
    doc["typeCode"] = typeCode
    doc["payerId"] = payerId
    doc["planCode"] = planCode
    doc["payerName"] = payerName
    doc["zmPayerId"] = zmPayerId
    doc["zmPayerName"] = zmPayerName
}

private fun Document.toInsurance(): Insurance =
    Insurance(
        typeCode = opt("typeCode"),
        payerId = opt("payerId"),
        planCode = opt("planCode"),
        payerName = opt("payerName"),
        zmPayerId = opt("zmPayerId"),
        zmPayerName = opt("zmPayerName")
    )

private fun Document.toSubscriber(): Subscriber =
    Subscriber(
        firstName = opt("firstName"),
        lastName = opt("lastName"),
        mi = opt("mi"),
        gender = opt("gender"),
        dob = opt("dob"),
        groupName = opt("groupName"),
        groupId = opt("groupId"),
        relationshipCode = opt("relationshipCode"),
        policyNumber = opt("policyNumber")
    )

private fun Subscriber.toDocument(): Document = doc {
    doc["firstName"] = firstName
    doc["lastName"] = lastName
    doc["mi"] = mi
    doc["gender"] = gender
    doc["dob"] = dob
    doc["groupName"] = groupName
    doc["groupId"] = groupId
    doc["relationshipCode"] = relationshipCode
    doc["policyNumber"] = policyNumber
}

fun Document.toPerson(): Person =
    Person(
        firstName = opt("firstName"),
        lastName = opt("lastName"),
        mi = opt("mi"),
        gender = opt("gender"),
        dob = opt("dob")
    )

fun Person.toDocument(): Document = doc {
    doc["firstName"] = firstName
    doc["lastName"] = lastName
    doc["mi"] = mi
    doc["gender"] = gender
    doc["dob"] = dob
}

private fun Document.toIssueAddress(): IssueAddress =
    IssueAddress(
        status = opt("status"),
        address1 = opt("address1"),
        address2 = opt("address2"),
        zip = opt("zip"),
        city = opt("city"),
        state = opt("state"),
        person = opt<Document>("person")?.toPerson()
    )

private fun IssueAddress.toDocument(): Document = doc {
    doc["status"] = status
    doc["address1"] = address1
    doc["address2"] = address2
    doc["zip"] = zip
    doc["city"] = city
    doc["state"] = state
    doc["person"] = person?.toDocument()
}

internal fun Document.toIssueExpert(): IssueExpert =
    IssueExpert(
        status = opt("status"),
        subject = opt("subject"),
        text = opt("text")
    )

internal fun IssueExpert.toDocument(): Document = doc {
    doc["status"] = status
    doc["subject"] = subject
    doc["text"] = text
}

