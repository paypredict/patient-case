package net.paypredict.patient.cases.pokitdok.eligibility

import com.mongodb.client.model.UpdateOptions
import net.paypredict.patient.cases.bson.`$set`
import net.paypredict.patient.cases.data.DBS
import net.paypredict.patient.cases.data.doc
import net.paypredict.patient.cases.data.opt
import net.paypredict.patient.cases.data.worklist.IssueEligibility
import net.paypredict.patient.cases.data.worklist.formatAs
import net.paypredict.patient.cases.pokitdok.client.EligibilityQuery
import net.paypredict.patient.cases.pokitdok.client.PokitDokApiException
import net.paypredict.patient.cases.pokitdok.client.digest
import net.paypredict.patient.cases.pokitdok.client.query
import org.bson.Document

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/2/2018.
 */
class EligibilityChecker(private val issue: IssueEligibility) {

    fun check(): EligibilityCheckRes {
        val subscriber = issue.subscriber ?: return EligibilityCheckRes.Error.subscriberIsRequired
        val insurance = issue.insurance ?: return EligibilityCheckRes.Error.insuranceIsRequired

        val member = subscriber.run {
            EligibilityQuery.Member(
                first_name = firstName ?: return EligibilityCheckRes.Error.subscriberFieldRequired("firstName"),
                last_name = lastName ?: return EligibilityCheckRes.Error.subscriberFieldRequired("lastName"),
                birth_date = dobAsLocalDate?.let { it formatAs EligibilityQuery.Member.dateFormat }
                    ?: return EligibilityCheckRes.Error.subscriberFieldRequired("dob"),
                id = policyNumber ?: return EligibilityCheckRes.Error.subscriberFieldRequired("policyNumber")
            )
        }

        val insurancePayerName = insurance.payerName
            ?: return EligibilityCheckRes.Error.insuranceFieldRequired("payerName")

        val tradingPartnerId = (DBS.Collections.PPPayers.lookupPkd()
            .find(doc {
                doc["input"] = insurancePayerName
            })
            .firstOrNull()
            ?.opt<String>("pkdId")
            ?: return EligibilityCheckRes.Error.insurancePayerNameNotFoundInPP(insurancePayerName))

        val query = EligibilityQuery(member = member, trading_partner_id = tradingPartnerId)
        val digest = query.digest()

        val collection = DBS.Collections.eligibility()
        val resFound = collection.find(doc { doc["_id"] = digest }).firstOrNull()
        if (resFound != null) return EligibilityCheckRes.Pass(digest, resFound)

        val res: Document = try {
            query.query { Document.parse(it.readText()) }
        } catch (e: Throwable) {
            return EligibilityCheckRes.Error.apiCallError(e)
        }
        collection.updateOne(
            doc { doc["_id"] = digest },
            doc {
                doc[`$set`] = doc {
                    doc["meta"] = res["meta"]
                    doc["data"] = res["data"]
                }
            },
            UpdateOptions().upsert(true)
        )

        return EligibilityCheckRes.Pass(digest, res)
    }

}

sealed class EligibilityCheckRes {
    class Pass(override val id: String, override val result: Document) :
        EligibilityCheckRes(), HasResult

    class Warn(override val id: String, override val result: Document, val warnings: List<Warning>) :
        EligibilityCheckRes(), HasResult

    class Error(val message: String) : EligibilityCheckRes() {
        companion object {
            val subscriberIsRequired = Error("subscriber is required")
            val insuranceIsRequired = Error("insurance is required")
            fun subscriberFieldRequired(field: String) = Error("subscriber field $field is required")
            fun insuranceFieldRequired(field: String) = Error("insurance field $field is required")
            fun insurancePayerNameNotFoundInPP(insurancePayerName: String) =
                Error("$insurancePayerName insurance payer name not found in ppPayers")

            fun apiCallError(x: Throwable) =
                Error(
                    when (x) {
                        is PokitDokApiException ->
                            try {
                                Document
                                    .parse(x.responseJson.toString())
                                    .opt<String>("data", "errors", "query")
                                    ?: x.message ?: x.javaClass.name
                            } catch (e: Throwable) {
                                x.message ?: x.javaClass.name
                            }
                        else ->
                            x.message ?: x.javaClass.name
                    }
                )
        }
    }

    interface HasResult {
        val id: String
        val result: Document
    }

    sealed class Warning(val message: String)
}
