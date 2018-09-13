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
            val primarySubscriber =
                case<List<*>>("case", "Case", "SubscriberDetails", "Subscriber")
                    ?.filterIsInstance<Document>()
                    ?.firstOrNull { it<String>("responsibilityCode") == "Primary" }
            val caseId = case["_id"] as String
            val caseIdFilter = doc { doc["_id"] = caseId }
            val issue = casesIssues.find(caseIdFilter).firstOrNull()
            if (issue == null) {
                if (primarySubscriber == null) {
                    val caseIssue = CaseIssue(
                        _id = caseId,
                        time = case.opt("date"),
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
                        eligibility = listOf(IssueEligibility(
                            insurance = Insurance(
                                typeCode = primarySubscriber("insuranceTypeCode"),
                                payerId = primarySubscriber("payerId"),
                                planCode = primarySubscriber("planCode"),
                                payerName = primarySubscriber("payerName"),
                                zmPayerId = primarySubscriber("zmPayerId"),
                                zmPayerName = primarySubscriber("zmPayerName")
                            ),
                            subscriber = Subscriber(
                                firstName = primarySubscriber("firstName"),
                                lastName = primarySubscriber("organizationNameOrLastName"),
                                mi = primarySubscriber("middleInitial"),
                                gender = primarySubscriber("gender"),
                                dob = primarySubscriber("dob"),
                                groupName = primarySubscriber("groupOrPlanName"),
                                groupId = primarySubscriber("groupOrPlanNumber"),
                                relationshipCode = primarySubscriber("relationshipCode"),
                                policyNumber = primarySubscriber("subscriberPolicyNumber")
                            )
                        ))
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
