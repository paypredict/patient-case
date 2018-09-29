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
import org.bson.Document

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/16/2018.
 */

fun updateCasesIssues(isInterrupted: () -> Boolean = { false }) {
    val casesRaw = DBS.Collections.casesRaw()
    val casesIssues = DBS.Collections.casesIssues()
    val filter = doc {
        doc["status.problems"] = doc { doc[`$exists`] = false }
    }
    val usStreet = UsStreet()
    for (case in casesRaw.find(filter)) {
        IssueCheckerAuto(usStreet, casesRaw, casesIssues, case)
            .check()
        if (isInterrupted()) break
    }
}

class CheckingException(override val message: String, var status: String = "ERROR") : Exception()

open class IssueChecker(
    open val usStreet: UsStreet = UsStreet()
) {
    var statusProblems = 0
    val statusValues = mutableMapOf<String, Any>()

    fun checkIssueAddress(issue: IssueAddress): Boolean {
        issue.status = null
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
                    throw CheckingException("smartyStreets api error: " + x.message, "ERROR")
                else -> {
                    x.printStackTrace()
                    throw CheckingException("smartyStreets api processing error "
                            + x.javaClass.name + ": "
                            + x.message, "ERROR")
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
            null -> true
            FootNote.Level.ERROR,
            FootNote.Level.WARNING -> {
                issue.status = maxFootNote.level.name
                statusValues["status.values.address"] =
                        Status(maxFootNote.level.name, candidate.analysis.footnotes).toDocument()
                statusProblems += 1
                false
            }
            FootNote.Level.INFO -> {
                issue.status = maxFootNote.level.name
                statusValues["status.values.address"] =
                        Status(maxFootNote.level.name, candidate.analysis.footnotes).toDocument()
                false
            }
        }
    }
}


private class IssueCheckerAuto(
    override val usStreet: UsStreet = UsStreet(),
    val casesRaw: MongoCollection<Document> = DBS.Collections.casesRaw(),
    val casesIssues: MongoCollection<Document> = DBS.Collections.casesIssues(),
    val case: Document
) : IssueChecker() {

    val caseId = case["_id"] as String
    val caseIdFilter = doc { doc["_id"] = caseId }
    val issue: Document? = casesIssues.find(caseIdFilter).firstOrNull()

    lateinit var caseIssue: CaseIssue

    fun check() {
        if (issue != null) return
        caseIssue = CaseIssue(
            _id = caseId,
            time = case.opt("date"),
            patient = case.casePatient()
        )

        checkNPI()
        checkSubscriber()
        checkAddress()

        casesIssues.insertOne(caseIssue.toDocument())
        casesRaw.updateOne(caseIdFilter, doc {
            doc[`$set`] = doc {
                doc["status.problems"] = statusProblems
                if (statusProblems > 0)
                    doc["status.value"] = "PROBLEMS"
                statusValues.forEach {
                    doc[it.key] = it.value
                }
            }
        })
    }

    private fun checkNPI() {
        val provider = case<Document>("case", "Case", "OrderingProvider", "Provider")
        if (provider == null) {
            caseIssue = caseIssue.copy(npi = listOf(IssueNPI(status = "NOT_FOUND")))
            statusProblems += 1
            statusValues["status.values.npi"] = Status("ERROR", "NPI not found").toDocument()
        } else {
            val npi = provider<String>("npi")
            val originalNPI = IssueNPI(
                npi = npi,
                name = Person(
                    firstName = provider<String>("firstName"),
                    lastName = provider<String>("lastName"),
                    mi = provider<String>("middleInitials")
                )
            )
            val apiNPI = IssueNPI(npi = npi)
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

                if (!apiNPI.nameEquals(originalNPI))
                    throw CheckingException("apiNPI != providerNPI", status = "Name Updated")

            } catch (e: CheckingException) {
                apiNPI.status = e.status
                apiNPI.error = e.message
                apiNPI.original = originalNPI
                statusProblems += 1
                statusValues["status.values.npi"] = Status(e.status, e.message).toDocument()
            }
            caseIssue = caseIssue.copy(npi = listOf(apiNPI))
        }
    }

    private fun checkSubscriber() {
        val issueEligibilityList = case.toSubscriberList().map {
            IssueEligibility(
                responsibility = it<String>("responsibilityCode"),
                insurance = it.toInsurance(),
                subscriber = it.toSubscriber()
            )
        }
        if (issueEligibilityList.isNotEmpty()) {
            caseIssue = caseIssue.copy(eligibility = issueEligibilityList)
            // TODO check Subscribers in issueEligibilityList
        } else {
            caseIssue = caseIssue.copy(eligibility = listOf(IssueEligibility(status = "NOT_FOUND")))
            statusProblems += 1
            statusValues["status.values.eligibility"] =
                    Status("WARNING", "No Subscribers found").toDocument()
        }
    }

    private fun checkAddress() {
        val person = case.findSubscriber() ?: case.findPatient()
        var original: IssueAddress? = null
        val issue = IssueAddress()
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

            if (issue.address1.isNullOrBlank()) throw CheckingException("Address not found")

            original = issue.copy()

            if (checkIssueAddress(issue))
                original = null

        } catch (x: Throwable) {
            val e = when (x) {
                is BadRequestException -> CheckingException("BadRequest: " + x.message, "ERROR")
                is CheckingException -> x
                else -> throw x
            }
            issue.status = e.status
            issue.error = e.message
            statusProblems += 1
            statusValues["status.values.address"] = Status(e.status, e.message).toDocument()
        }
        caseIssue = caseIssue.copy(address = listOfNotNull(original, issue))
    }

    companion object {
        private fun Document.toSubscriberList(): List<Document> =
            opt<List<*>>("case", "Case", "SubscriberDetails", "Subscriber")
                ?.asSequence()
                ?.filterIsInstance<Document>()
                ?.toList()
                ?: emptyList()

        private fun Document.findSubscriber(): Document? =
            toSubscriberList().run {
                firstOrNull { it<String>("responsibilityCode") == "Primary" } ?: firstOrNull()
            }

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