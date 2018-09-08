package net.paypredict.patient.cases.data.worklist

import com.vaadin.flow.templatemodel.Encode
import net.paypredict.patient.cases.*
import net.paypredict.patient.cases.data.DateToDateTimeBeanEncoder
import net.paypredict.patient.cases.data.doc
import net.paypredict.patient.cases.data.opt
import org.bson.Document
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/19/2018.
 */

@VaadinBean
data class CaseIssues(
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
    @DataView("Status", order = 10)
    override var status: String?,

    @DataView("NPI", order = 20)
    var npi: String?,

    @DataView("First Name", order = 30)
    var firstName: String?,

    @DataView("Last Name", order = 40)
    var lastName: String?
) : IssuesStatus {
    companion object : IssuesClass<IssueNPI> {
        override val caption = "Physician NPI"
        override val beanType = IssueNPI::class.java
        override val metaData = metaDataMap<IssueNPI>()
    }
}

@VaadinBean
data class IssueEligibility(
    @DataView("Status", order = 10, flexGrow = 1)
    override var status: String? = null,

    @DataView("Insurance", order = 20, flexGrow = 5)
    var insurance: Insurance? = null,

    @DataView("Subscriber", order = 30, flexGrow = 2)
    var subscriber: Subscriber? = null

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

fun Document.toCaseIssues(): CaseIssues =
    CaseIssues(
        _id = get("_id").toString(),
        time = opt<Date>("time"),
        patient = opt<Document>("patient")?.toPerson(),
        npi = opt<List<*>>("issue", "npi")
            ?.filterIsInstance<Document>()
            ?.map { it.toIssueNPI() }
            ?: emptyList(),
        eligibility = opt<List<*>>("issue", "eligibility")
            ?.filterIsInstance<Document>()
            ?.map { it.toIssueEligibility() }
            ?: emptyList(),
        address = opt<List<*>>("issue", "address")
            ?.filterIsInstance<Document>()
            ?.map { it.toIssueAddress() }
            ?: emptyList(),
        expert = opt<List<*>>("issue", "expert")
            ?.filterIsInstance<Document>()
            ?.map { it.toIssueExpert() }
            ?: emptyList()
    )

fun CaseIssues.toDocument(): Document = doc {
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

private fun Document.toIssueNPI(): IssueNPI =
    IssueNPI(
        status = opt("status"),
        npi = opt("npi"),
        firstName = opt("firstName"),
        lastName = opt("lastName")
    )

private fun IssueNPI.toDocument(): Document = doc {
    doc["status"] = status
    doc["npi"] = npi
    doc["firstName"] = firstName
    doc["lastName"] = lastName
}


private fun Document.toIssueEligibility(): IssueEligibility =
    IssueEligibility(
        status = opt("status"),
        insurance = opt<Document>("insurance")?.toInsurance(),
        subscriber = opt<Document>("subscriber")?.toSubscriber()
    )

fun IssueEligibility.toDocument(): Document = doc {
    doc["status"] = status
    doc["insurance"] = insurance?.toDocument()
    doc["subscriber"] = subscriber?.toDocument()
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
        dob = opt("dob")
    )

fun Person.toDocument(): Document = doc {
    doc["firstName"] = firstName
    doc["lastName"] = lastName
    doc["mi"] = mi
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

