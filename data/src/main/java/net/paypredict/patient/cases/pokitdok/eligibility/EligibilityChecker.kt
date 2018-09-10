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

        val zmPayerId = insurance.zmPayerId
            ?: return EligibilityCheckRes.Error.insuranceFieldRequired("zmPayerId")

        val tradingPartnerId = (DBS.Collections.PPPayers.lookupPkd()
            .find(doc {
                doc["zmPayerId"] = zmPayerId
            })
            .firstOrNull()
            ?.opt<String>("pkdId")
            ?: return EligibilityCheckRes.Error.insuranceZirMedPayerIdNotFoundInPP(zmPayerId))

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
        val result: EligibilityCheckRes =
            if (res.opt<Boolean>("data", "coverage", "active") == true)
                EligibilityCheckRes.Pass(digest, res) else
                EligibilityCheckRes.Warn(digest, res,
                    listOf(EligibilityCheckRes.Warning("Coverage isn't active")))

        collection.updateOne(
            doc { doc["_id"] = digest },
            doc {
                doc[`$set`] = doc {
                    doc["status"] = when (result) {
                        is EligibilityCheckRes.Pass -> "pass"
                        is EligibilityCheckRes.Warn -> "warn"
                        is EligibilityCheckRes.Error -> "error"
                    }
                    doc["meta"] = res["meta"]
                    doc["data"] = res["data"]
                    if (result is EligibilityCheckRes.Warn) {
                        doc["warnings"] = result.warnings.map { it.message }
                    }
                }
            },
            UpdateOptions().upsert(true)
        )

        return result
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
            fun insuranceZirMedPayerIdNotFoundInPP(zmPayerId: String) =
                Error("ZirMed payer id $zmPayerId not found in ppPayers")

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

    class Warning(val message: String)
}

fun Document.toEligibilityCheckRes(): EligibilityCheckRes? =
    when (opt<String>("status")) {
        null,
        "pass" -> EligibilityCheckRes.Pass(get("_id") as String, this)

        "warn" -> EligibilityCheckRes.Warn(get("_id") as String, this,
            opt<List<*>>("warnings")?.mapNotNull { it.toWarning() } ?: emptyList()
        )

        "error" -> EligibilityCheckRes.Error(
            opt<String>("error") ?: "Unknown Eligibility Error at ${get("_id")}"
        )

        else -> EligibilityCheckRes.Error(
            "Invalid Eligibility Status '${opt<String>("status")}' at ${get("_id")}"
        )
    }

private fun Any?.toWarning(): EligibilityCheckRes.Warning? =
    when (this) {
        is String -> EligibilityCheckRes.Warning(this)
        else -> null
    }
