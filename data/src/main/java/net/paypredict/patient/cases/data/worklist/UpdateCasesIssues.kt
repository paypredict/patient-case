package net.paypredict.patient.cases.data.worklist

import com.mongodb.client.MongoCollection
import com.smartystreets.api.exceptions.BadRequestException
import com.smartystreets.api.exceptions.SmartyException
import com.smartystreets.api.us_street.Lookup
import com.smartystreets.api.us_street.MatchType
import net.paypredict.patient.cases.apis.npiregistry.NpiRegistry
import net.paypredict.patient.cases.apis.npiregistry.NpiRegistryException
import net.paypredict.patient.cases.apis.smartystreets.FootNote
import net.paypredict.patient.cases.apis.smartystreets.UsStreet
import net.paypredict.patient.cases.apis.smartystreets.footNoteSet
import net.paypredict.patient.cases.mongo.*
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityCheckRes
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityChecker
import org.bson.Document

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/16/2018.
 */

fun updateCasesIssues(isInterrupted: () -> Boolean = { false }) {
    val casesRaw = DBS.Collections.casesRaw()
    val casesIssues = DBS.Collections.casesIssues()
    val items: List<Document> = casesRaw
        .find(doc { doc["status.problems"] = doc { doc[`$exists`] = false } })
        .projection(doc { })
        .toList()
    val usStreet = UsStreet()
    val payerLookup = PayerLookup()
    for (item in items) {
        val case = casesRaw.find(item).firstOrNull() ?: continue
        val issueCheckerAuto =
            IssueCheckerAuto(
                usStreet = usStreet,
                payerLookup = payerLookup,
                casesRaw = casesRaw,
                casesIssues = casesIssues,
                case = case
            )
        issueCheckerAuto.check()
        if (isInterrupted()) break
    }
}

class CheckingException(override val message: String, var status: IssuesStatus? = null) : Exception()

data class IssueAddressCheckRes(
    val hasProblems: Boolean = false,
    val status: Status? = null
)

open class IssueChecker(
    open val usStreet: UsStreet = UsStreet()
) {
    var statusProblems = 0
    val statusValues: MutableMap<String, Any?> = mutableMapOf()

    fun checkIssueAddress(issue: IssueAddress): IssueAddressCheckRes {
        issue.status = IssueAddress.Status.Unchecked
        issue.footnotes = null

        val lookup = Lookup().apply {
            match = MatchType.RANGE
            street = issue.address1
            street2 = issue.address2
            city = issue.city
            state = issue.state
            zipCode = issue.zip
        }

        try {
            usStreet.send(lookup)
        } catch (x: Throwable) {
            when (x) {
                is SmartyException ->
                    throw CheckingException("smartyStreets api error: " + x.message)
                else -> {
                    x.printStackTrace()
                    throw CheckingException(
                        "smartyStreets api processing error "
                                + x.javaClass.name + ": "
                                + x.message
                    )
                }
            }
        }

        val candidate = lookup.result.firstOrNull()
            ?: throw throw CheckingException("Address not found by smartyStreets api")

        issue.footnotes = candidate.analysis.footnotes

        issue.address1 = candidate.deliveryLine1
        issue.address2 = candidate.deliveryLine2
        val components = candidate.components
            ?: throw CheckingException("candidate.components not found in smartyStreets api response")
        issue.city = components.cityName
        issue.state = components.state
        issue.zip = components.zipCode + components.plus4Code.let {
            when {
                it.isNullOrBlank() -> ""
                else -> "-$it"
            }
        }

        val maxFootNote = candidate.analysis.footNoteSet.asSequence().maxBy { it.level }
        return when (maxFootNote?.level) {
            null -> {
                issue.status = IssueAddress.Status.Confirmed
                IssueAddressCheckRes()
            }
            FootNote.Level.ERROR -> {
                issue.status = IssueAddress.Status.Error(maxFootNote.label, maxFootNote.note)
                IssueAddressCheckRes(
                    hasProblems = true,
                    status = Status(maxFootNote.level.name, candidate.analysis.footnotes)
                )
            }
            FootNote.Level.WARNING -> {
                issue.status = IssueAddress.Status.Corrected
                IssueAddressCheckRes(
                    hasProblems = true,
                    status = Status(maxFootNote.level.name, candidate.analysis.footnotes)
                )
            }
            FootNote.Level.INFO -> {
                issue.status = IssueAddress.Status.Confirmed
                IssueAddressCheckRes(
                    hasProblems = false,
                    status = Status(maxFootNote.level.name, candidate.analysis.footnotes)
                )
            }
        }
    }

    fun updateStatusValuesAddress(res: IssueAddressCheckRes) {
        if (res.hasProblems) statusProblems++
        statusValues["status.values.address"] = res.status?.toDocument()
    }
}


internal class IssueCheckerAuto(
    override val usStreet: UsStreet = UsStreet(),
    val payerLookup: PayerLookup = PayerLookup(),
    val casesRaw: MongoCollection<Document> = DBS.Collections.casesRaw(),
    val casesIssues: MongoCollection<Document> = DBS.Collections.casesIssues(),
    val case: Document
) : IssueChecker() {

    private val caseId = case["_id"] as String
    private val caseIdFilter = doc { doc["_id"] = caseId }
    private val issue: Document? = casesIssues.find(caseIdFilter).firstOrNull()

    lateinit var caseIssue: CaseIssue

    fun check() {
        if (issue != null) return
        caseIssue = CaseIssue(
            _id = caseId,
            time = case.opt("date"),
            patient = case.casePatient()
        )

        val hasResultByResponsibilityMap: MutableMap<String, EligibilityCheckRes.HasResult> = mutableMapOf()

        checkNPI()
        checkSubscriber(caseIssue.patient) { hasResult ->
            responsibility?.also { hasResultByResponsibilityMap[it] = hasResult }
        }
        checkAddress(
            ResponsibilityOrder.values()
                .asSequence()
                .mapNotNull { hasResultByResponsibilityMap[it.name]?.findSubscriberAddress() }
                .firstOrNull())

        casesIssues.insertOne(caseIssue.toDocument())
        casesRaw.updateOne(caseIdFilter, doc {
            doc[`$set`] = doc {
                doc["status.problems"] = statusProblems
                if (statusProblems > 0)
                    doc["status.value"] = "PROBLEMS"
                statusValues.forEach { (key, value) ->
                    if (value != null)
                        doc[key] = value else
                        doc -= key
                }
            }
        })
    }

    private fun checkNPI() {
        val provider = case<Document>("case", "Case", "OrderingProvider", "Provider")
        if (provider == null) {
            caseIssue = caseIssue.copy(npi = listOf(IssueNPI(status = IssueNPI.Status.Unchecked)))
            statusProblems += 1
            statusValues["status.values.npi"] = Status("ERROR", "NPI not found").toDocument()
        } else {
            val npi = provider<String>("npi")
            val originalNPI = IssueNPI(
                status = IssueNPI.Status.Original,
                npi = npi,
                name = Person(
                    firstName = provider<String>("firstName"),
                    lastName = provider<String>("lastName"),
                    mi = provider<String>("middleInitials")
                )
            )
            val apiNPI = originalNPI.copy(
                status = IssueNPI.Status.Unchecked
            )
            try {
                if (npi == null) throw CheckingException("Case Provider npi is null")

                val npiRes = try {
                    NpiRegistry.find(npi)
                } catch (e: NpiRegistryException) {
                    throw CheckingException(e.message)
                }

                val results = npiRes<List<*>>("results")
                    ?: throw CheckingException("Invalid API response: results in null")
                if (results.size != 1)
                    throw CheckingException("Invalid API response: results.size != 1 (${results.size})")

                val res = results.firstOrNull() as? Document
                    ?: throw CheckingException("Invalid API response: results[0] isn't Document")

                val basic = res<Document>("basic")

                apiNPI.name = Person(
                    firstName = basic<String>("first_name"),
                    lastName = basic<String>("last_name"),
                    mi = basic<String>("middle_name")
                )
                apiNPI.taxonomies = res<List<*>>("taxonomies")
                    ?.asSequence()
                    ?.filterIsInstance<Document>()
                    ?.map { it.toTaxonomy() }
                    ?.toList()
                        ?: emptyList()

                val status = when (apiNPI.nameEquals(originalNPI)) {
                    true -> IssueNPI.Status.Confirmed
                    false -> IssueNPI.Status.Corrected
                }
                apiNPI.status = status
                statusValues["status.values.npi"] = Status(status.name).toDocument()

            } catch (e: CheckingException) {
                val status =
                    e.status as? IssueNPI.Status
                        ?: IssueNPI.Status.Error("Checking Error", e.message)
                apiNPI.status = status
                statusProblems += 1
                statusValues["status.values.npi"] = Status(status.name, e.message).toDocument()
            }
            caseIssue = caseIssue.copy(npi = listOf(originalNPI, apiNPI))
        }
    }

    private fun checkSubscriber(patient: Person?, onHasResult: OnHasResult) {
        val issueEligibilityList = case.toSubscriberList().map {
            IssueEligibility(
                status = IssueEligibility.Status.Original,
                origin = "casesRaw",
                responsibility = it<String>("responsibilityCode"),
                insurance = it.toInsurance(),
                subscriber = it.toSubscriber(),
                subscriberRaw = it.toSubscriberRaw()
            )
        }
        if (issueEligibilityList.isNotEmpty()) {
            val eligibilityCheckContext = EligibilityCheckContext(payerLookup, patient, onHasResult)
            val checkedEligibility = issueEligibilityList.map {
                it.checkEligibility(eligibilityCheckContext)
            }
            caseIssue = caseIssue.copy(eligibility = issueEligibilityList + checkedEligibility)
            if (checkedEligibility.any { it.status != IssueEligibility.Status.Confirmed }) {
                statusProblems += 1
                statusValues["status.values.eligibility"] =
                        Status("WARNING").toDocument()
            }
        } else {
            caseIssue = caseIssue.copy(
                eligibility = listOf(
                    IssueEligibility(
                        status = IssueEligibility.Status.Missing,
                        origin = "checking",
                        responsibility = ResponsibilityOrder.Primary.name
                    )
                )
            )
            statusProblems += 1
            statusValues["status.values.eligibility"] =
                    Status("WARNING", "No Subscribers found").toDocument()
        }
    }

    private fun checkAddress(subscriberAddress: IssueAddress?) {
        val person = case.findPatient()
        val history = mutableListOf<IssueAddress>()
        var issue = IssueAddress(status = IssueAddress.Status.Unchecked)
        var checkRes: IssueAddressCheckRes? = null
        try {
            if (person == null) throw CheckingException("Case Subscriber and Patient is null")
            issue.person = Person(
                firstName = person("firstName"),
                lastName = person("organizationNameOrLastName") ?: person("firstName"),
                mi = person("middleInitial"),
                gender = person("gender"),
                dob = person("dob") ?: person("dateOfBirth")
            )
            issue.address1 = person("address1")
            issue.address2 = person("address2")
            issue.zip = person("zip")
            issue.city = person("city")
            issue.state = person("state")

            history += issue.copy(status = IssueAddress.Status.Original)

            var hasProblems: Boolean
            try {
                if (issue.address1.isNullOrBlank()) throw CheckingException("Address not found")
                checkRes = checkIssueAddress(issue)
                hasProblems = checkRes.hasProblems
            } catch (e: CheckingException) {
                hasProblems = true
                if (subscriberAddress == null) throw e
                history += issue.copy(status = e.toStatus(), error = e.message)
            }

            if (hasProblems && subscriberAddress != null) {
                history += subscriberAddress.copy(status = IssueAddress.Status.Corrected)
                issue = subscriberAddress
                checkRes = checkIssueAddress(issue)
            }

        } catch (x: Throwable) {
            val e = when (x) {
                is BadRequestException -> CheckingException("BadRequest: " + x.message)
                is CheckingException -> x
                else -> throw x
            }
            val status = e.toStatus()
            issue.status = status
            issue.error = e.message
            checkRes = IssueAddressCheckRes(
                hasProblems = true,
                status = Status(status.name, e.message)
            )
        }
        checkRes?.also {
            updateStatusValuesAddress(it)
        }
        caseIssue = caseIssue.copy(address = history + issue)
    }

    private fun CheckingException.toStatus(): IssueAddress.Status =
        status as? IssueAddress.Status
            ?: IssueAddress.Status.Error("Checking Error", message)

    companion object {
        private fun Document.toSubscriberList(): List<Document> =
            opt<List<*>>("case", "Case", "SubscriberDetails", "Subscriber")
                ?.asSequence()
                ?.filterIsInstance<Document>()
                ?.toList()
                ?: emptyList()

        private fun Document.findPatient(): Document? {
            return opt<Document>("case", "Case", "Patient")
        }

        private fun Document.toSubscriber(): Subscriber =
            Subscriber(
                firstName = opt("firstName"),
                lastName = opt("organizationNameOrLastName"),
                mi = opt("middleInitial"),
                gender = opt("gender"),
                dob = opt("dob"),
                groupName = opt("groupOrPlanName"),
                groupId = opt("groupOrPlanNumber"),
                relationshipCode = opt("relationshipCode"),
                policyNumber = opt("subscriberPolicyNumber")
            )

        private fun Document.toInsurance(): Insurance =
            Insurance(
                typeCode = opt("insuranceTypeCode"),
                payerId = opt("payerId"),
                planCode = opt("planCode"),
                payerName = opt("payerName"),
                zmPayerId = opt("zmPayerId"),
                zmPayerName = opt("zmPayerName")
            )
    }

}

fun EligibilityCheckRes.HasResult.findSubscriberAddress(): IssueAddress? {
    val address = result.opt<Document>("data", "subscriber", "address")
            ?: result.opt<Document>("data", "dependent", "address")
            ?: return null
    val lines = address<List<*>>("address_lines")
        ?.filterIsInstance<String>() ?: return null
    return IssueAddress(
        address1 = lines.getOrNull(0) ?: return null,
        address2 = lines.getOrNull(1),
        zip = address("zipcode") ?: return null,
        city = address("city") ?: return null,
        state = address("state") ?: return null
    )
}

typealias OnHasResult = IssueEligibility.(EligibilityCheckRes.HasResult) -> Unit

class EligibilityCheckContext(
    val payerLookup: PayerLookup,
    val patient: Person? = null,
    val onHasResult: OnHasResult? = null
)

fun IssueEligibility.checkEligibility(context: EligibilityCheckContext): IssueEligibility =
    deepCopy().apply {
        var isPayerCheckable = false
        insurance?.run {
            val payer = payerName?.let { context.payerLookup[it] }
            zmPayerId = payer?.value
            if (payer?.checkable == true) {
                isPayerCheckable = true
            }
        }

        var isSubscriberCheckable = false
        subscriber?.run {
            val hasPolicyNumber = !policyNumber.isNullOrBlank()
            context.patient?.also { patient ->
                patient.gender?.also { if (!(it == "Unknown" || it.isBlank())) gender = it }
                patient.dobAsLocalDate?.also { if (it.year > 1900) dob = patient.dob }
                when (relationshipCode) {
                    "SEL", "UNK" -> {
                        if (firstName.isNullOrBlank()) firstName = patient.firstName
                        if (lastName.isNullOrBlank()) lastName = patient.lastName
                        if (mi.isNullOrBlank()) mi = patient.mi
                    }
                }
            }
            isSubscriberCheckable = hasPolicyNumber && !firstName.isNullOrBlank() && !lastName.isNullOrBlank()
        }

        val checkable: Boolean = isPayerCheckable && isSubscriberCheckable
        status = when {
            checkable -> {
                val checkRes = EligibilityChecker(this).check()
                if (checkRes is EligibilityCheckRes.HasResult) {
                    eligibility = checkRes.id
                    subscriber?.run {
                        checkRes.result.opt<Document>("data", "subscriber")
                            ?.also { res ->
                                firstName = res("first_name")
                                lastName = res("last_name")
                                mi = res("middle_name")
                                gender = res<String>("gender")?.capitalize()
                                dob = res<String>("birth_date")?.convertDateTime(
                                    EligibilityCheckRes.dateFormat,
                                    Person.dateFormat
                                )
                            }
                    }
                    context.onHasResult?.invoke(this@checkEligibility, checkRes)
                } else {
                    eligibility = null
                }

                when (checkRes) {
                    is EligibilityCheckRes.Pass -> IssueEligibility.Status.Confirmed
                    is EligibilityCheckRes.Warn -> IssueEligibility.Status.Problem(
                        "Problem With Eligibility", checkRes.warnings.joinToString { it.message }
                    )
                    is EligibilityCheckRes.Error -> IssueEligibility.Status.Problem(
                        "Checking Error", checkRes.message
                    )
                }
            }
            else -> IssueEligibility.Status.Unchecked
        }
    }

private fun Document?.casePatient(): Person? =
    this<Document>("case", "Case", "Patient")
        ?.let { patient ->
            Person(
                firstName = patient("firstName"),
                lastName = patient("lastName"),
                mi = patient("middleInitials"),
                gender = patient("gender"),
                dob = patient("dateOfBirth")
            )
        }