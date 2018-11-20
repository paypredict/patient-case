package net.paypredict.patient.cases.pokitdok.eligibility

import com.mongodb.client.FindIterable
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.UpdateOptions
import net.paypredict.patient.cases.data.cases.CasesLog
import net.paypredict.patient.cases.data.cases.LogLevel
import net.paypredict.patient.cases.data.cases.insert
import net.paypredict.patient.cases.data.worklist.IssueEligibility
import net.paypredict.patient.cases.data.worklist.formatAs
import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo.`$set`
import net.paypredict.patient.cases.mongo.doc
import net.paypredict.patient.cases.mongo.opt
import net.paypredict.patient.cases.pokitdok.client.EligibilityQuery
import net.paypredict.patient.cases.pokitdok.client.PokitDokApiException
import net.paypredict.patient.cases.pokitdok.client.digest
import net.paypredict.patient.cases.pokitdok.client.query
import net.paypredict.patient.cases.toTitleCase
import org.bson.Document
import java.time.format.DateTimeFormatter

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/2/2018.
 */
class EligibilityChecker(private val issue: IssueEligibility, private val newCasesLog: () -> CasesLog) {

    fun check(): EligibilityCheckRes {
        val subscriber = issue.subscriber ?: return EligibilityCheckRes.Error.subscriberIsRequired
        val insurance = issue.insurance ?: return EligibilityCheckRes.Error.insuranceIsRequired

        val member = subscriber.run {
            EligibilityQuery.Member(
                id = policyNumber ?: return EligibilityCheckRes.Error.subscriberFieldRequired("policyNumber"),
                first_name = firstName ?: return EligibilityCheckRes.Error.subscriberFieldRequired("firstName"),
                last_name = lastName ?: return EligibilityCheckRes.Error.subscriberFieldRequired("lastName"),
                birth_date = dobAsLocalDate?.let { it formatAs EligibilityQuery.Member.dateFormat }
                    ?: return EligibilityCheckRes.Error.subscriberFieldRequired("dob"),
                gender = gender
            )
        }

        val zmPayerId = insurance.zmPayerId
            ?: return EligibilityCheckRes.Error.insuranceFieldRequired("zmPayerId")

        val pkdPayer = PayersData().findPkdPayer(zmPayerId)
        if (pkdPayer?.notAvailable == true) {
            return EligibilityCheckRes.NotAvailable
        }

        val tradingPartnerId = pkdPayer?.id
            ?: return EligibilityCheckRes.Error.insuranceZirMedPayerIdNotFoundInPP(zmPayerId)

        val query = EligibilityQuery(member = member, trading_partner_id = tradingPartnerId)
        val digest = query.digest()

        val collection = DBS.Collections.eligibility()

        val foundRes =
            collection
                .find(doc { self["_id"] = digest })
                .firstOrNull()
                ?.toEligibilityCheckRes()
        when {
            foundRes is EligibilityCheckRes.Warn -> {
                val rejectReason = RejectReason.from(foundRes.result)
                if (rejectReason?.cacheable == true)
                    return foundRes
                if (rejectReason == null) {
                    val rejectReasonValue = RejectReason.optRejectReason(foundRes.result)
                    if (rejectReasonValue != null)
                        newCasesLog()
                            .copy(
                                level = LogLevel.WARNING,
                                source = ".system",
                                action = "EligibilityChecker.check",
                                message = "unknown data.reject_reason: $rejectReasonValue",
                                ext = foundRes.result
                            )
                            .insert()
                }
            }
            foundRes is EligibilityCheckRes.Error -> {
                newCasesLog()
                    .copy(
                        level = LogLevel.ERROR,
                        source = ".system",
                        action = "EligibilityChecker.check -> error",
                        message = foundRes.message
                    )
                    .insert()
            }

            foundRes != null ->
                return foundRes
        }

        val res: Document = try {
            query.query { Document.parse(it.readText()) }
        } catch (e: Throwable) {
            return EligibilityCheckRes.Error.apiCallError(e)
        }
        val result: EligibilityCheckRes =
            res.eligibilityCheckResFromAPI(digest)

        collection.updateOne(
            doc { self["_id"] = digest },
            doc {
                self[`$set`] = doc {
                    self["status"] = when (result) {
                        is EligibilityCheckRes.Pass -> "pass"
                        is EligibilityCheckRes.Warn -> "warn"
                        is EligibilityCheckRes.NotAvailable -> "ntav"
                        is EligibilityCheckRes.Error -> "error"
                    }
                    self["meta"] = res["meta"]
                    self["data"] = res["data"]
                    if (result is EligibilityCheckRes.Warn) {
                        self["message"] = result.message
                        self["warnings"] = result.warnings.map { it.message }
                    }
                }
            },
            UpdateOptions().upsert(true)
        )

        return result
    }

    private fun Document.eligibilityCheckResFromAPI(digest: String): EligibilityCheckRes {
        if (opt<Boolean>("data", "coverage", "active") == true)
            return EligibilityCheckRes.Pass(digest, this)
        val rejectReason: String? = opt("data", "reject_reason")
        return EligibilityCheckRes.Warn(
            digest, this,
            rejectReason?.toTitleCase() ?: "Coverage isn't active",
            emptyList()
        )
    }

}

@Suppress("EnumEntryName")
enum class RejectReason(val cacheable: Boolean = true) {
    invalid_subscriber_insured_id,
    patient_birth_date_mismatch,
    provider_not_on_file,
    invalid_subscriber_insured_name,
    subscriber_insured_not_found,
    no_response_transaction_terminated(cacheable = false),
    invalid_participant_id,
    unable_to_respond_now(cacheable = false),
    invalid_provider_id,
    subscriber_insured_not_in_group_plan,
    invalid_subscriber_insured_gender,
    invalid_date_of_service,
    input_errors(cacheable = false);

    companion object {

        fun from(name: String?): RejectReason? =
            try {
                name?.let { RejectReason.valueOf(it) }
            } catch (e: IllegalArgumentException) {
                null
            }

        fun from(apiRes: Document?): RejectReason? =
            from(optRejectReason(apiRes))

        fun optRejectReason(apiRes: Document?) =
            apiRes?.opt<String>("data", "reject_reason")
    }
}

sealed class EligibilityCheckRes {
    class Pass(
        override val id: String, override val result: Document
    ) :
        EligibilityCheckRes(), HasResult

    class Warn(
        override val id: String, override val result: Document,
        val message: String,
        val warnings: List<Warning>
    ) :
        EligibilityCheckRes(), HasResult

    object NotAvailable : EligibilityCheckRes()

    class Error(val message: String) : EligibilityCheckRes() {
        companion object {
            val subscriberIsRequired = Error("subscriber is required")
            val insuranceIsRequired = Error("insurance is required")
            fun subscriberFieldRequired(field: String) = Error("subscriber field $field is required")
            fun insuranceFieldRequired(field: String) = Error("insurance field $field is required")
            fun insuranceZirMedPayerIdNotFoundInPP(zmPayerId: String) =
                Error("Cortex Payer id $zmPayerId not found in ppPayers")

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

    companion object {
        val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}

fun Document.toEligibilityCheckRes(): EligibilityCheckRes =
    when (opt<String>("status")) {
        null,
        "pass" -> EligibilityCheckRes.Pass(get("_id") as String, this)

        "warn" -> EligibilityCheckRes.Warn(get("_id") as String, this,
            opt("message") ?: "Warnings",
            opt<List<*>>("warnings")?.mapNotNull { it.toWarning() } ?: emptyList()
        )

        "ntav" -> EligibilityCheckRes.NotAvailable

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
        val displayName: String,
        val payerName: String
    ) : Doc

    val zirmedPayers: Map<String, ZirMedPayer> by lazy {
        findAndMap(
            collection = DBS.Collections.PPPayers.zirmedPayers()
        ) { doc ->
            ZirMedPayer(
                _id = doc["_id"] as String,
                displayName = doc.opt<String>("displayName") ?: "???",
                payerName = doc.opt<String>("Payer_Name") ?: "???"
            )
        }
    }

    private class UsersData {
        data class UsersMatchPayer(
            override val _id: String,
            val zmPayerId: String,
            val pkdPayerId: String?,
            val notAvailable: Boolean = false
        ) : Doc

        private val usersMatchPayers: Map<String, UsersMatchPayer> by lazy {
            findAndMap(
                collection = DBS.Collections.PPPayers.usersMatchPayers()
            ) { doc ->
                UsersMatchPayer(
                    _id = doc["_id"] as String,
                    zmPayerId = doc.opt<String>("zmPayerId")!!,
                    pkdPayerId = doc.opt<String>("pkdPayerId"),
                    notAvailable = doc.opt<Boolean>("notAvailable") ?: false
                )
            }
        }

        val usersMatchPayersByZmPayerId: Map<String, UsersMatchPayer> by lazy {
            usersMatchPayers.values.map { it.zmPayerId to it }.toMap()
        }

    }

    private var usersData: UsersData = UsersData()

    data class MatchPayer(
        override val _id: String,
        val displayName: String?,
        val zmPayerId: String?
    ) : Doc

    private val matchPayers: Map<String, MatchPayer> by lazy {
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

    private val matchPayersByZmPayerId: Map<String, MatchPayer> by lazy {
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
                    self["data.name"] = 1
                    self["data.payer_id"] = 1
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

    data class PkdPayer(
        val id: String?,
        val notAvailable: Boolean = false
    )

    fun findPkdPayer(zmPayerId: String?): PkdPayer? {
        val usersMatchPayer =
            usersData.usersMatchPayersByZmPayerId[zmPayerId]
        if (usersMatchPayer != null) {
            return PkdPayer(usersMatchPayer.pkdPayerId, usersMatchPayer.notAvailable)
        }
        return PkdPayer(matchPayersByZmPayerId[zmPayerId]?._id)
    }

    fun findPkdPayerId(zmPayerId: String?): String? =
        findPkdPayer(zmPayerId)?.id

    fun updateUsersMatchPayersRecord(zmPayerId: String, pkdPayerId: String?, notAvailable: Boolean = false) {
        DBS.Collections.PPPayers.usersMatchPayers().updateOne(
            doc { self["_id"] = zmPayerId },
            doc {
                self[`$set`] = doc {
                    self["zmPayerId"] = zmPayerId
                    self["pkdPayerId"] = pkdPayerId
                    self["notAvailable"] = notAvailable
                }
            },
            UpdateOptions().upsert(true)
        )
        usersData = UsersData()
    }

    fun removeUsersMatchPayersRecord(zmPayerId: String) {
        DBS.Collections.PPPayers.usersMatchPayers().deleteOne(doc {
            self["_id"] = zmPayerId
        })
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
