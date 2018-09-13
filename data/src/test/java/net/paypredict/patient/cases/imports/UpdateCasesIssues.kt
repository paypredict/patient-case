package net.paypredict.patient.cases.imports

import net.paypredict.patient.cases.bson.`$set`
import net.paypredict.patient.cases.data.DBS
import net.paypredict.patient.cases.data.doc
import net.paypredict.patient.cases.data.invoke
import net.paypredict.patient.cases.data.opt
import net.paypredict.patient.cases.data.worklist.*
import org.bson.Document

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/16/2018.
 */
object UpdateCasesIssues {
    @JvmStatic
    fun main(args: Array<String>) {
        UpdateCasesIssues.update()
    }

    private fun update() {
        val casesRaw = DBS.Collections.casesRaw()
        val casesIssues = DBS.Collections.casesIssues()
        casesRaw.find().forEach { case ->
            val subscribers = case<List<*>>("case", "Case", "SubscriberDetails", "Subscriber")
                ?.filterIsInstance<Document>()
            val subscriber = subscribers
                ?.firstOrNull { it<String>("responsibilityCode") == "Primary" }
                ?: subscribers?.firstOrNull()
            val caseId = case["_id"] as String
            val caseIdFilter = doc { doc["_id"] = caseId }
            val issue = casesIssues.find(caseIdFilter).firstOrNull()
            if (issue == null) {
                val casePatient = case<Document>("case", "Case", "Patient")
                    ?.let { patient ->
                        Person(
                            firstName = patient("firstName"),
                            lastName = patient("lastName"),
                            mi = patient("middleInitials"),
                            gender = patient("gender"),
                            dob = patient("dateOfBirth")
                        )
                    }
                if (subscriber == null) {
                    val caseIssue = CaseIssue(
                        _id = caseId,
                        time = case.opt("date"),
                        patient = casePatient,
                        eligibility = listOf(IssueEligibility(status = "NOT_FOUND"))
                    )
                    casesIssues.insertOne(caseIssue.toDocument())
                    casesRaw.updateOne(caseIdFilter, doc {
                        doc[`$set`] = doc {
                            doc["status.value"] = "ELIGIBILITY_PROBLEM"
                            doc["status.values.eligibility"] =
                                    Status("WARNING", "Primary Subscriber not found").toDocument()
                        }
                    })
                } else {
                    val caseIssue = CaseIssue(
                        _id = caseId,
                        time = case.opt("date"),
                        patient = casePatient,
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
                    casesIssues.insertOne(caseIssue.toDocument())
                    casesRaw.updateOne(caseIdFilter, doc {
                        doc[`$set`] = doc {
                            doc["status.value"] = "SOLVED"
                        }
                    })
                }
            }
        }
    }
}
