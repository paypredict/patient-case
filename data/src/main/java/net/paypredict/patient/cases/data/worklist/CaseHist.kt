package net.paypredict.patient.cases.data.worklist

import com.mongodb.client.MongoCollection
import com.vaadin.flow.templatemodel.Encode
import net.paypredict.patient.cases.DataView
import net.paypredict.patient.cases.MetaData
import net.paypredict.patient.cases.VaadinBean
import net.paypredict.patient.cases.apis.smartystreets.FootNote
import net.paypredict.patient.cases.apis.smartystreets.FootNoteSet
import net.paypredict.patient.cases.data.DateToDateTimeBeanEncoder
import net.paypredict.patient.cases.data.cases.insert
import net.paypredict.patient.cases.data.cases.toCasesLog
import net.paypredict.patient.cases.metaDataMap
import net.paypredict.patient.cases.mongo.*
import org.bson.Document
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/19/2018.
 */

@VaadinBean
data class CaseHist(
    @DataView("_id", isVisible = false)
    val _id: String,

    @DataView(label = "Accession", isVisible = false)
    var accession: String? = null,

    @set:Encode(DateToDateTimeBeanEncoder::class)
    @DataView("Date.Time", order = 10)
    var time: Date? = null,

    @DataView("Patient", order = 20, isVisible = false)
    var patient: Person? = null,

    @DataView("NPI", order = 30)
    var npi: List<IssueNPI> = emptyList(),

    @DataView("Primary Insurance", isVisible = false)
    var insurancePrimary: List<IssueEligibility> = emptyList(),

    @DataView("Secondary Insurance", isVisible = false)
    var insuranceSecondary: List<IssueEligibility> = emptyList(),

    @DataView("Tertiary Insurance", isVisible = false)
    var insuranceTertiary: List<IssueEligibility> = emptyList(),

    @DataView("Address", order = 50)
    var address: List<IssueAddress> = emptyList(),

    @DataView("AI", order = 60)
    var expert: List<IssueExpert> = emptyList(),

    @DataView("Status", isVisible = false)
    var status: CaseStatus? = null
)

var CaseHist.eligibility: List<IssueEligibility>
    get() =
        insurancePrimary + insuranceSecondary + insuranceTertiary
    set(new) {
        insurancePrimary = emptyList()
        insuranceSecondary = emptyList()
        insuranceTertiary = emptyList()
        new.forEach {
            when (it.responsibility) {
                "Primary" -> insurancePrimary += it
                "Secondary" -> insuranceSecondary += it
                "Tertiary" -> insuranceTertiary += it
            }
        }
    }

class UpdateContext(
    val source: String,
    val action: String,
    val cases: MongoCollection<Document> = DBS.Collections.cases(),
    val casesLog: MongoCollection<Document> = DBS.Collections.casesLog(),
    val message: String? = null,
    val user: String?
)

fun CaseHist.update(
    context: UpdateContext,
    comment: String? = null,
    status: CaseStatus? = null
) {
    val filter = _id._id()
    context.cases.upsertOne(filter, doc {
        self[`$set`] = doc {
            self["hist.npi"] = npi.map { it.toDocument() }
            self["hist.insurancePrimary"] = insurancePrimary.map { it.toDocument() }
            self["hist.insuranceSecondary"] = insuranceSecondary.map { it.toDocument() }
            self["hist.insuranceTertiary"] = insuranceTertiary.map { it.toDocument() }
            self["hist.address"] = address.map { it.toDocument() }
            self["hist.expert"] = expert.map { it.toDocument() }

            self["attr.npi"] = npi.lastOrNull()?.toDocument()
            self["attr.insurancePrimary"] = insurancePrimary.lastOrNull()?.toDocument()
            self["attr.insuranceSecondary"] = insuranceSecondary.lastOrNull()?.toDocument()
            self["attr.insuranceTertiary"] = insuranceTertiary.lastOrNull()?.toDocument()
            self["attr.eligibility"] = toEligibilityAttr()?.toDocument()
            self["attr.address"] = address.lastOrNull()?.toDocument()
            self["attr.expert"] = expert.lastOrNull()?.toDocument()

            self["comment"] = comment

            if (status != null) {
                self["status"] = status.toDocument()
            }

            self["doc.updated"] = Date()
        }
    })

    toCasesLog(
        source = context.source,
        action = context.action,
        message = context.message,
        user = context.user,
        status = status
    ).insert(context.casesLog)
}

private fun CaseHist.toEligibilityAttr(): IssueEligibility? =
    listOf(insurancePrimary, insuranceSecondary, insuranceTertiary)
        .mapNotNull { it.lastOrNull() }
        .sortedBy { it.status }
        .firstOrNull()

interface IssueItem<S : IssuesStatus> {
    var status: S?
}

interface IssuesClass<T : IssueItem<*>> {
    val caption: String
    val beanType: Class<T>
    val metaData: Map<String, MetaData<T>>
}

interface IssuesStatus {
    val name: String
    val passed: Boolean
    val type: Type

    enum class Type {
        OK, INFO, WARN, QUESTION, ERROR
    }
}

interface IssuesStatusExt : IssuesStatus {
    val ext: Map<String, Any>
}

fun IssuesStatus.toDocument(): Document = doc {
    val status = this@toDocument
    if (status is IssuesStatusExt) {
        status.ext.forEach { key, value ->
            self[key] = value
        }
    }
    self["name"] = name
    self["passed"] = passed
}

fun List<IssueEligibility>.findBest(caseStatus: CaseStatus): IssueEligibility? =
    when {
        caseStatus.timeout -> null // == use original xml
        caseStatus.resolved ->
            findPassed()
                ?: reversed().firstOrNull { it.status?.notEmpty == true }
        else ->
            findPassed()
    }

fun <T : IssueItem<S>, S : IssuesStatus> List<T>.findPassed(): T? =
    reversed().firstOrNull { it.status?.passed == true }

@VaadinBean
data class IssueNPI(
    @DataView("Status", flexGrow = 1, order = 10)
    override var status: Status? = null,

    @DataView("NPI", flexGrow = 1, order = 20)
    var npi: String? = null,

    @DataView("Name", flexGrow = 3, order = 30)
    var name: Person? = null,

    @DataView("Taxonomies", order = 200, isVisible = false)
    var taxonomies: List<Taxonomy> = emptyList()

) : IssueItem<IssueNPI.Status> {

    sealed class Status(
        override val name: String,
        override val passed: Boolean,
        override val type: IssuesStatus.Type
    ) : IssuesStatus {
        object Original : Status("Original", false, IssuesStatus.Type.WARN)
        object Unchecked : Status("Unchecked", false, IssuesStatus.Type.WARN)
        object Corrected : Status("Corrected", false, IssuesStatus.Type.WARN)
        object Confirmed : Status("Confirmed", true, IssuesStatus.Type.OK)
        class Error(
            val error: String? = null,
            val message: String? = null
        ) : Status("Error", false, IssuesStatus.Type.ERROR), IssuesStatusExt {
            override val ext: Map<String, Any> by lazy {
                mutableMapOf<String, Any>().also { ext ->
                    error?.let { ext["error"] = it }
                    message?.let { ext["message"] = it }
                }
            }
        }

        override fun toString(): String = name

        companion object
    }

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

fun Document.toIssueNPIStatus(): IssueNPI.Status? =
    when (opt<String>("name")) {
        IssueNPI.Status.Original.name -> IssueNPI.Status.Original
        IssueNPI.Status.Unchecked.name -> IssueNPI.Status.Unchecked
        IssueNPI.Status.Corrected.name -> IssueNPI.Status.Corrected
        IssueNPI.Status.Confirmed.name -> IssueNPI.Status.Confirmed
        IssueNPI.Status.Error::class.java.simpleName -> IssueNPI.Status.Error(
            error = opt<String>("error"),
            message = opt<String>("message")
        )
        else -> null
    }

fun IssueNPI.nameEquals(other: IssueNPI, ignoreCase: Boolean = true, compareMI: Boolean = true): Boolean {
    if (!(name?.firstName ?: "").equals(other.name?.firstName ?: "", ignoreCase = ignoreCase)) return false
    if (!(name?.lastName ?: "").equals(other.name?.lastName ?: "", ignoreCase = ignoreCase)) return false
    if (compareMI && !(name?.mi ?: "").equals(other.name?.mi ?: "", ignoreCase = ignoreCase)) return false
    return true
}


@VaadinBean
data class IssueEligibility(
//    @get:Encode(IssuesStatusToStatusBeanEncoder::class)
    @DataView("Status", order = 10, flexGrow = 1)
    override var status: Status? = null,

    @DataView("origin", order = 20, isVisible = false)
    var origin: String? = null,

    @DataView("Responsibility", order = 30, flexGrow = 1)
    var responsibility: String? = null,

    @DataView("Insurance", order = 40, flexGrow = 5)
    var insurance: Insurance? = null,

    @DataView("Subscriber", order = 50, flexGrow = 2)
    var subscriber: Subscriber? = null,

    @DataView("eligibility._id", isVisible = false)
    var eligibility: String? = null,

    @DataView("Subscriber Raw", isVisible = false)
    var subscriberRaw: Map<String, String> = emptyMap()

) : IssueItem<IssueEligibility.Status> {

    sealed class Status(
        override val name: String,
        override val passed: Boolean,
        override val type: IssuesStatus.Type,
        val notEmpty: Boolean = true
    ) : IssuesStatus, Comparable<Status> {
        object Missing : Status("Missing", false, IssuesStatus.Type.QUESTION, false)
        object Original : Status("Original", false, IssuesStatus.Type.INFO, false)
        object Unchecked : Status("Unchecked", false, IssuesStatus.Type.WARN)
        object NotAvailable : Status("NotAvailable", true, IssuesStatus.Type.INFO)
        object Confirmed : Status("Confirmed", true, IssuesStatus.Type.OK)
        class Problem(
            val error: String? = null,
            val message: String? = null
        ) : Status("Problem", false, IssuesStatus.Type.ERROR), IssuesStatusExt {
            override val ext: Map<String, Any> by lazy {
                mutableMapOf<String, Any>().also { ext ->
                    error?.let { ext["error"] = it }
                    message?.let { ext["message"] = it }
                }
            }
        }

        override fun toString(): String = name

        override fun compareTo(other: Status): Int =
            ord.compareTo(other.ord)

        companion object {
            private val Status.ord: Int
                get() = when (this) {
                    Missing -> -2000
                    is Problem -> -1000
                    Unchecked -> -100
                    Original -> 0
                    NotAvailable -> 10
                    Confirmed -> 1000
                }
        }
    }

    companion object : IssuesClass<IssueEligibility> {
        override val caption = "Patient Eligibility"
        override val beanType = IssueEligibility::class.java
        override val metaData = metaDataMap<IssueEligibility>()
    }
}

fun IssueEligibility.deepCopy(): IssueEligibility = copy(
    insurance = insurance?.copy(),
    subscriber = subscriber?.copy()
)

fun Document.toIssueEligibilityStatus(): IssueEligibility.Status? =
    when (opt<String>("name")) {
        IssueEligibility.Status.Missing.name -> IssueEligibility.Status.Missing
        IssueEligibility.Status.Original.name -> IssueEligibility.Status.Original
        IssueEligibility.Status.Unchecked.name -> IssueEligibility.Status.Unchecked
        IssueEligibility.Status.NotAvailable.name -> IssueEligibility.Status.NotAvailable
        IssueEligibility.Status.Confirmed.name -> IssueEligibility.Status.Confirmed
        IssueEligibility.Status.Problem::class.java.simpleName -> IssueEligibility.Status.Problem(
            error = opt<String>("error"),
            message = opt<String>("message")
        )
        else -> null
    }

enum class ResponsibilityOrder {
    Primary, Secondary, Tertiary
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

    @DataView("Cortex Payer ID")
    var zmPayerId: String? = null,

    @DataView("Cortex Payer Name")
    var zmPayerName: String? = null
)

/**
 * `Case.SubscriberDetails.Subscriber`
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
        get() = dob asLocalDateOrNull dateFormat
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
    override fun toString(): String =
        listOfNotNull(firstName, lastName, mi, gender, dob)
            .asSequence()
            .filter { it.isNotBlank() }
            .joinToString()

    companion object {
        val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    }

    val dobAsLocalDate: LocalDate?
        get() = dob asLocalDateOrNull dateFormat
}

infix fun String?.asLocalDateOrNull(dateFormat: DateTimeFormatter): LocalDate? =
    if (this == null) null else
        try {
            LocalDate.parse(this, dateFormat)
        } catch (e: DateTimeParseException) {
            null
        }

infix fun LocalDate.formatAs(dateFormat: DateTimeFormatter): String =
    dateFormat.format(this)

fun String.convertDateTime(from: DateTimeFormatter, to: DateTimeFormatter): String =
    to.format(from.parse(this))

@VaadinBean
data class IssueAddress(
    @DataView("Status", flexGrow = 0, order = 10)
    override var status: Status? = null,

    @DataView("Address 1", order = 20)
    var address1: String? = null,

    @DataView("Address 2", order = 30)
    var address2: String? = null,

    @DataView("ZIP", order = 40)
    var zip: String? = null,

    @DataView("City", order = 50)
    var city: String? = null,

    @DataView("State", flexGrow = 0, order = 60)
    var state: String? = null,

    @DataView("Person", order = 100, isVisible = false)
    var person: Person? = null,

    @DataView("Footnotes code", order = 200, isVisible = false)
    var footnotes: String? = null,

    @DataView("Error", order = 201, isVisible = false)
    var error: String? = null

) : IssueItem<IssueAddress.Status> {

    sealed class Status(
        override val name: String,
        override val passed: Boolean,
        override val type: IssuesStatus.Type,
        val footnotes: String? = null
    ) :
        IssuesStatusExt {
        class Missing(footnotes: String? = null) : Status("Missing", false, IssuesStatus.Type.WARN, footnotes)
        class Original(footnotes: String? = null) : Status("Original", false, IssuesStatus.Type.INFO, footnotes)
        class Unchecked(footnotes: String? = null) : Status("Unchecked", false, IssuesStatus.Type.WARN, footnotes)
        class Corrected(footnotes: String? = null) : Status("Corrected", true, IssuesStatus.Type.OK, footnotes)
        class Confirmed(footnotes: String? = null) : Status("Confirmed", true, IssuesStatus.Type.OK, footnotes)
        class Error(
            val error: String? = null,
            val message: String? = null,
            footnotes: String? = null
        ) : Status("Error", false, IssuesStatus.Type.ERROR, footnotes), IssuesStatusExt {
            override val ext: Map<String, Any> by lazy {
                super.ext + mutableMapOf<String, Any>().also { ext ->
                    error?.let { ext["error"] = it }
                    message?.let { ext["message"] = it }
                }
            }
        }

        override val ext: Map<String, Any> by lazy {
            mutableMapOf<String, Any>().also { ext ->
                footnotes?.let { ext["footnotes"] = it }
            }
        }

        override fun toString(): String = name

        companion object
    }

    @DataView("Footnotes", flexGrow = 3, order = 70)
    var footNoteSet: FootNoteSet = emptySet()
        get() = FootNote.decodeFootNoteSet(footnotes)
        set(value) {
            field = value
            footnotes = FootNote.encodeFootNoteSet(value)
        }

    companion object : IssuesClass<IssueAddress> {
        override val caption = "Patient Address"
        override val beanType = IssueAddress::class.java
        override val metaData = metaDataMap<IssueAddress>()
    }
}

fun Document.toIssueAddressStatus(): IssueAddress.Status? =
    when (opt<String>("name")) {
        IssueAddress.Status.Missing::class.simpleName -> IssueAddress.Status.Missing(opt<String>("footnotes"))
        IssueAddress.Status.Original::class.simpleName -> IssueAddress.Status.Original(opt<String>("footnotes"))
        IssueAddress.Status.Unchecked::class.simpleName -> IssueAddress.Status.Unchecked(opt<String>("footnotes"))
        IssueAddress.Status.Corrected::class.simpleName -> IssueAddress.Status.Corrected(opt<String>("footnotes"))
        IssueAddress.Status.Confirmed::class.simpleName -> IssueAddress.Status.Confirmed(opt<String>("footnotes"))
        IssueAddress.Status.Error::class.simpleName -> IssueAddress.Status.Error(
            error = opt<String>("error"),
            message = opt<String>("message"),
            footnotes = (opt<String>("footnotes"))
        )
        else -> null
    }

@VaadinBean
data class IssueExpert(
    @DataView("Status", order = 10)
    override var status: Status? = null,

    @DataView("Subject", order = 20)
    var subject: String? = null,

    @DataView("Text", order = 30)
    var text: String? = null

) : IssueItem<IssueExpert.Status> {

    sealed class Status(
        override val name: String,
        override val passed: Boolean,
        override val type: IssuesStatus.Type
    ) : IssuesStatus {
        class Problem(
            val error: String? = null,
            val message: String? = null
        ) : Status("Problem", false, IssuesStatus.Type.ERROR), IssuesStatusExt {
            override val ext: Map<String, Any> by lazy {
                mutableMapOf<String, Any>().also { ext ->
                    error?.let { ext["error"] = it }
                    message?.let { ext["message"] = it }
                }
            }
        }

        override fun toString(): String = name

        companion object
    }

    companion object : IssuesClass<IssueExpert> {
        override val caption = "Expert Advice"
        override val beanType = IssueExpert::class.java
        override val metaData = metaDataMap<IssueExpert>()
    }
}

fun Document.toIssueExpertStatus(): IssueExpert.Status? =
    when (opt<String>("name")) {
        IssueExpert.Status.Problem::class.java.simpleName -> IssueExpert.Status.Problem(
            error = opt<String>("error"),
            message = opt<String>("message")
        )
        else -> null
    }

fun Document.toCaseHist(): CaseHist =
    CaseHist(
        _id = get("_id").toString(),
        accession = opt("case", "Case", "accessionNumber"),
        time = opt<Date>("doc", "created"),
        patient = casePatient(),
        npi = opt<List<*>>("hist", "npi")
            ?.asSequence()
            ?.filterIsInstance<Document>()
            ?.map { it.toIssueNPI() }
            ?.toList()
            ?: emptyList(),
        insurancePrimary = opt<List<*>>("hist", "insurancePrimary")
            ?.toInsuranceList()
            ?: emptyList(),
        insuranceSecondary = opt<List<*>>("hist", "insuranceSecondary")
            ?.toInsuranceList()
            ?: emptyList(),
        insuranceTertiary = opt<List<*>>("hist", "insuranceTertiary")
            ?.toInsuranceList()
            ?: emptyList(),
        address = opt<List<*>>("hist", "address")
            ?.asSequence()
            ?.filterIsInstance<Document>()
            ?.map { it.toIssueAddress() }
            ?.toList()
            ?: emptyList(),
        expert = opt<List<*>>("hist", "expert")
            ?.asSequence()
            ?.filterIsInstance<Document>()
            ?.map { it.toIssueExpert() }
            ?.toList()
            ?: emptyList(),
        status = opt<Document>("status")?.toCaseStatus()
    )

private fun List<*>.toInsuranceList(): List<IssueEligibility> =
    asSequence()
        .filterIsInstance<Document>()
        .map { it.toIssueEligibility() }
        .toList()

fun Document.toIssueNPI(): IssueNPI =
    IssueNPI(
        status = opt<Document>("status")?.toIssueNPIStatus(),
        npi = opt("npi"),
        name = opt<Document>("name")?.toPerson(),
        taxonomies = opt<List<*>>("taxonomies")
            ?.asSequence()
            ?.filterIsInstance<Document>()
            ?.map { it.toTaxonomy() }
            ?.toList()
            ?: emptyList()
    )

fun IssueNPI.toDocument(): Document = doc {
    self["status"] = status?.toDocument()
    self["npi"] = npi
    self["name"] = name?.toDocument()
    sinn("taxonomies", taxonomies.map { it.toDocument() })
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
    sinn("primary", primary)
    sinn("code", code)
    sinn("license", license)
    sinn("desc", desc)
    sinn("state", state)
}


private fun Document.toIssueEligibility(): IssueEligibility =
    IssueEligibility(
        status = opt<Document>("status")?.toIssueEligibilityStatus(),
        origin = opt("origin"),
        responsibility = opt("responsibility"),
        insurance = opt<Document>("insurance")?.toInsurance(),
        subscriber = opt<Document>("subscriber")?.toSubscriber(),
        eligibility = opt<String>("eligibility"),
        subscriberRaw = toSubscriberRaw()
    )

fun IssueEligibility.toDocument(): Document = doc {
    self["status"] = status?.toDocument()
    self["origin"] = origin
    self["responsibility"] = responsibility
    self["insurance"] = insurance?.toDocument()
    self["subscriber"] = subscriber?.toDocument()
    self["eligibility"] = eligibility
    self["subscriberRaw"] = subscriberRaw.toSubscriberRawDocument()
}

private fun Insurance.toDocument(): Document = doc {
    self["typeCode"] = typeCode
    self["payerId"] = payerId
    self["planCode"] = planCode
    self["payerName"] = payerName
    self["zmPayerId"] = zmPayerId
    self["zmPayerName"] = zmPayerName
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
    self["firstName"] = firstName
    self["lastName"] = lastName
    sinn("mi", mi)
    sinn("gender", gender)
    sinn("dob", dob)
    sinn("groupName", groupName)
    sinn("groupId", groupId)
    sinn("relationshipCode", relationshipCode)
    self["policyNumber"] = policyNumber
}

fun Document.toSubscriberRaw(): Map<String, String> =
    mutableMapOf<String, String>().also { map ->
        entries.forEach { entry ->
            val key = entry.key
            (entry.value as? String)?.let { map[key] = it }
        }
    }

fun Map<String, String>.toSubscriberRawDocument(): Document =
    Document(this)


fun Document.toPerson(): Person =
    Person(
        firstName = opt("firstName"),
        lastName = opt("lastName"),
        mi = opt("mi"),
        gender = opt("gender"),
        dob = opt("dob")
    )

fun Person.toDocument(): Document = doc {
    self["firstName"] = firstName
    self["lastName"] = lastName
    sinn("mi", mi)
    sinn("gender", gender)
    sinn("dob", dob)
}

private fun Document.toIssueAddress(): IssueAddress =
    IssueAddress(
        status = opt<Document>("status")?.toIssueAddressStatus(),
        address1 = opt("address1"),
        address2 = opt("address2"),
        zip = opt("zip"),
        city = opt("city"),
        state = opt("state"),
        footnotes = opt("footnotes"),
        person = opt<Document>("person")?.toPerson()
    )

fun IssueAddress.toDocument(): Document = doc {
    self["status"] = status?.toDocument()
    self["address1"] = address1
    self["address2"] = address2
    self["zip"] = zip
    self["city"] = city
    self["state"] = state
    self["footnotes"] = footnotes
    self["person"] = person?.toDocument()
}

internal fun Document.toIssueExpert(): IssueExpert =
    IssueExpert(
        status = opt<Document>("status")?.toIssueExpertStatus(),
        subject = opt("subject"),
        text = opt("text")
    )

internal fun IssueExpert.toDocument(): Document = doc {
    self["status"] = status?.toDocument()
    self["subject"] = subject
    self["text"] = text
}

