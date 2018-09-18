package net.paypredict.patient.cases.data.worklist

import com.mongodb.client.MongoCollection
import net.paypredict.patient.cases.apis.npiregistry.NpiRegistry
import net.paypredict.patient.cases.apis.npiregistry.NpiRegistryException
import net.paypredict.patient.cases.bson.`$ne`
import net.paypredict.patient.cases.bson.`$set`
import net.paypredict.patient.cases.data.DBS
import net.paypredict.patient.cases.data.doc
import net.paypredict.patient.cases.data.invoke
import net.paypredict.patient.cases.data.opt
import org.bson.Document

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/16/2018.
 */

fun updateCasesIssues(isInterrupted: () -> Boolean = { false }) {
    val casesRaw = DBS.Collections.casesRaw()
    val casesIssues = DBS.Collections.casesIssues()
    val filter = doc { doc["status.problems"] = doc { doc[`$ne`] = 0 } }
    for (case in casesRaw.find(filter)) {
        IssuesChecker(casesRaw, casesIssues, case).check()
        if (isInterrupted()) break
    }
}

private class IssuesChecker(
    val casesRaw: MongoCollection<Document> = DBS.Collections.casesRaw(),
    val casesIssues: MongoCollection<Document> = DBS.Collections.casesIssues(),
    val case: Document
) {

    val caseId = case["_id"] as String
    val caseIdFilter = doc { doc["_id"] = caseId }
    val issue: Document? = casesIssues.find(caseIdFilter).firstOrNull()

    lateinit var caseIssue: CaseIssue
    var statusProblems = 0
    val statusValues = mutableMapOf<String, Any>()


    fun check() {
        if (issue != null) return
        caseIssue = CaseIssue(
            _id = caseId,
            time = case.opt("date"),
            patient = case.casePatient()
        )

        checkNPI()
        checkSubscriber()

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

    private class CheckingException(override val message: String, var status: String = "ERROR") : Exception()

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
                if (npi == null) throw CheckingException("Case Provider npi in null")

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
        val subscribers = case<List<*>>("case", "Case", "SubscriberDetails", "Subscriber")
            ?.filterIsInstance<Document>()
        val subscriber = subscribers
            ?.firstOrNull { it<String>("responsibilityCode") == "Primary" }
            ?: subscribers?.firstOrNull()
        if (subscriber != null) {
            caseIssue = caseIssue.copy(
                eligibility = listOf(
                    IssueEligibility(
                        insurance = Insurance(
                            typeCode = subscriber("insuranceTypeCode"),
                            payerId = subscriber("payerId"),
                            planCode = subscriber("planCode"),
                            payerName = subscriber("payerName"),
                            zmPayerId = subscriber("zmPayerId"),
                            zmPayerName = subscriber("zmPayerName")
                        ),
                        subscriber = Subscriber(
                            firstName = subscriber("firstName"),
                            lastName = subscriber("organizationNameOrLastName"),
                            mi = subscriber("middleInitial"),
                            gender = subscriber("gender"),
                            dob = subscriber("dob"),
                            groupName = subscriber("groupOrPlanName"),
                            groupId = subscriber("groupOrPlanNumber"),
                            relationshipCode = subscriber("relationshipCode"),
                            policyNumber = subscriber("subscriberPolicyNumber")
                        )
                    )
                )
            )
        } else {
            caseIssue = caseIssue.copy(
                eligibility = listOf(IssueEligibility(status = "NOT_FOUND"))
            )
            statusProblems += 1
            statusValues["status.values.eligibility"] =
                    Status("WARNING", "Primary Subscriber not found").toDocument()
        }
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