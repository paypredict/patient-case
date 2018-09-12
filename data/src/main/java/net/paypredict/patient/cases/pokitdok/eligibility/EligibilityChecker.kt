package net.paypredict.patient.cases.pokitdok.eligibility

import com.mongodb.client.FindIterable
import com.mongodb.client.MongoCollection
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

        val tradingPartnerId = PayersData().findPkdPayerId(zmPayerId)
            ?: return EligibilityCheckRes.Error.insuranceZirMedPayerIdNotFoundInPP(zmPayerId)

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
                EligibilityCheckRes.Warn(
                    digest, res,
                    listOf(EligibilityCheckRes.Warning("Coverage isn't active"))
                )

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

class PayersData {
    data class ZirMedPayer(
        override val _id: String,
        val displayName: String
    ) : Doc

    val zirmedPayers: Map<String, ZirMedPayer> by lazy {
        findAndMap(
            collection = DBS.Collections.PPPayers.zirmedPayers()
        ) { doc ->
            ZirMedPayer(
                _id = doc["_id"] as String,
                displayName = doc.opt<String>("displayName") ?: "???"
            )
        }
    }

    private class UsersData {
        data class UsersMatchPayer(
            override val _id: String,
            val zmPayerId: String,
            val pkdPayerId: String?
        ) : Doc

        private val usersMatchPayers: Map<String, UsersMatchPayer> by lazy {
            findAndMap(
                collection = DBS.Collections.PPPayers.usersMatchPayers()
            ) { doc ->
                UsersMatchPayer(
                    _id = doc["_id"] as String,
                    zmPayerId = doc.opt<String>("zmPayerId")!!,
                    pkdPayerId = doc.opt<String>("pkdPayerId")
                )
            }
        }

        val usersMatchPayersByZmPayerId: Map<String, UsersMatchPayer> by lazy {
            usersMatchPayers.values.map { it.zmPayerId to it }.toMap()
        }

        val usersMatchPayersByZmPkdPayerId: Map<String, UsersMatchPayer> by lazy {
            usersMatchPayers.values
                .mapNotNull { payer -> payer.pkdPayerId?.let { it to payer } }
                .toMap()
        }

    }

    private var usersData: UsersData = UsersData()

    data class MatchPayer(
        override val _id: String,
        val displayName: String?,
        val zmPayerId: String?
    ) : Doc

    val matchPayers: Map<String, MatchPayer> by lazy {
        findAndMap(
            collection = DBS.Collections.PPPayers.matchPayers()
        ) { doc ->
            MatchPayer(
                _id = doc["_id"] as String,
                displayName = doc.opt<String>("displayName"),
                zmPayerId = doc.opt<String>("zmPayerId")
            )
        }
    }

    val matchPayersByZmPayerId: Map<String, MatchPayer> by lazy {
        matchPayers.values.mapNotNull { it.zmPayerId?.let { key -> key to it } }.toMap()
    }

    data class TradingPartner(
        override val _id: String,
        val name: String?,
        val payerId: String?
    ) : Doc {
        val displayName: String?
            get() = payerId?.let { "$name [ $it ]" } ?: name
    }

    val tradingPartners: Map<String, TradingPartner> by lazy {
        findAndMap(
            collection = DBS.Collections.tradingPartners(),
            prepare = {
                projection(doc {
                    doc["data.name"] = 1
                    doc["data.payer_id"] = 1
                })
            }
        ) { doc ->
            TradingPartner(
                _id = doc["_id"] as String,
                name = doc.opt<String>("data", "name"),
                payerId = doc.opt<String>("data", "payer_id")
            )
        }
    }

    fun findPkdPayerId(zmPayerId: String?): String? {
        val usersMatchPayer =
            usersData.usersMatchPayersByZmPayerId[zmPayerId]
        if (usersMatchPayer != null) return usersMatchPayer.pkdPayerId
        return matchPayersByZmPayerId[zmPayerId]?._id
    }

    fun updateUsersPayerIds(pkdPayerId: String?, zmPayerId: String) {
        DBS.Collections.PPPayers.usersMatchPayers().updateOne(
            doc { doc["_id"] = zmPayerId },
            doc {
                doc[`$set`] = doc {
                    doc["zmPayerId"] = zmPayerId
                    doc["pkdPayerId"] = pkdPayerId
                }
            },
            UpdateOptions().upsert(true)
        )
        usersData = UsersData()
    }

    companion object {
        private inline fun <reified T : Doc> findAndMap(
            collection: MongoCollection<Document>,
            prepare: FindIterable<Document>.() -> FindIterable<Document> = { this },
            map: (Document) -> T
        ): Map<String, T> =
            mutableMapOf<String, T>().also { result ->
                collection.find().prepare().forEach { doc ->
                    map(doc).also { result[it._id] = it }
                }
            }

        interface Doc {
            @Suppress("PropertyName")
            val _id: String
        }
    }
}
