package net.paypredict.patient.cases.data.worklist

import com.vaadin.flow.templatemodel.Encode
import net.paypredict.patient.cases.*
import net.paypredict.patient.cases.data.DateToDateTimeBeanEncoder
import net.paypredict.patient.cases.mongo.*
import org.bson.Document
import java.util.*

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/19/2018.
 */
@VaadinBean
data class CaseAttr(
    @DataView("_id", isVisible = false)
    val _id: String,

    @set:Encode(DateToDateTimeBeanEncoder::class)
    @DataView(
        label = "Date.Time", order = 20,
        docKey = "file.created"
    )
    var date: Date? = null,

    @DataView(
        label = "Accession", order = 10,
        docKey = "case.Case.accessionNumber"
    )
    var accession: String? = null,

    @DataView(
        label = "Payer Name", order = 30,
        flexGrow = 5,
        docKey = "attr.insurancePrimary.insurance.payerName"
    )
    var payerName: String? = null,

    @DataView(
        label = "NPI", order = 50,
        docKey = "attr.npi.status",
        srtKey = "attr.npi.status.name"
    )
    var npi: IssueNPI.Status? = null,

    @DataView(
        label = "Eligibility", order = 60,
        docKey = "attr.eligibility.status",
        srtKey = "attr.eligibility.status.name"
    )
    var eligibility: IssueEligibility.Status? = null,

    @DataView(
        label = "Address", order = 70,
        docKey = "attr.address.status",
        srtKey = "attr.address.status.name"
    )
    var address: IssueAddress.Status? = null,

    @DataView(
        label = "AI", order = 80,
        docKey = "attr.expert.status",
        srtKey = "attr.expert.status.name"
    )
    var expert: IssueExpert.Status? = null,

    @DataView(
        label = "Comment", order = 90,
        docKey = "comment",
        projectionKeys = ["case.Case.caseComments"],
        isVisible = false
    )
    var comment: String? = null,

    @DataView(
        label = "Status", order = 1,
        docKey = "status",
        srtKey = "status.value",
        filterKeys = [
            "status.checked",
            "status.passed",
            "status.resolved",
            "status.timeout",
            "status.sent"
        ]
    )
    var status: CaseStatus? = null

)

val CASE_ATTR_META_DATA_MAP: Map<String, MetaData<CaseAttr>> by lazy { metaDataMap<CaseAttr>() }

fun Document.toCaseAttr(): CaseAttr =
    CaseAttr(
        _id = get("_id").toString(),
        date = opt<Date>("file", "created"),
        accession = opt("case", "Case", "accessionNumber"),
        payerName = opt("attr", "insurancePrimary", "insurance", "payerName"),
        npi = opt<Document>("attr", "npi", "status")?.toIssueNPIStatus(),
        eligibility = opt<Document>("attr", "eligibility", "status")?.toIssueEligibilityStatus(),
        address = opt<Document>("attr", "address", "status")?.toIssueAddressStatus(),
        expert = opt<Document>("attr", "expert", "status")?.toIssueExpertStatus(),
        comment = opt("comment") ?: opt("case", "Case", "caseComments"),
        status = opt<Document>("status")?.toCaseStatus()
    )

@VaadinBean
data class CaseStatus(
    val checked: Boolean = false,
    val passed: Boolean = false,
    val resolved: Boolean = false,
    val timeout: Boolean = false,
    val hold: Boolean = false,
    val sent: Boolean = false,
    val error: Boolean = false
) {
    val value: String
        get() = sum?.name ?: ""

    val sum: Sum?
        get() = when {
            error -> Sum.ERROR
            sent -> Sum.SENT
            hold -> Sum.HOLD
            timeout -> Sum.TIMEOUT
            resolved -> Sum.RESOLVED
            passed -> Sum.PASSED
            checked -> Sum.CHECKED
            else -> null
        }

    enum class Sum {
        ERROR, SENT, HOLD, TIMEOUT, RESOLVED, PASSED, CHECKED
    }
}

fun Document.toCaseStatus(): CaseStatus =
    CaseStatus(
        checked = opt("checked") ?: false,
        passed = opt("passed") ?: false,
        resolved = opt("resolved") ?: false,
        timeout = opt("timeout") ?: false,
        hold = opt("hold") ?: false,
        sent = opt("sent") ?: false,
        error = opt("error") ?: false
    )

fun CaseStatus.toDocument(): Document =
    doc {
        self["checked"] = checked
        self["passed"] = passed
        self["resolved"] = resolved
        self["timeout"] = timeout
        self["hold"] = hold
        self["sent"] = sent
        self["error"] = error
        self["value"] = value
    }

val CaseStatus.isEditable: Boolean
    get() = value == "CHECKED" || value == "HOLD"

val CaseAttr.isEditable: Boolean
    get() = status?.isEditable ?: false


fun CaseAttr.hold(
    hold: Boolean,
    user: CasesUser?,
    cases: DocumentMongoCollection = DBS.Collections.cases()
) {
    cases
        .find(_id._id())
        .firstOrNull()
        ?.toCaseHist()
        ?.apply {
            update(
                context = UpdateContext(
                    source = ".user",
                    action = "case.hold",
                    message = "hold = $hold",
                    cases = cases,
                    user = user?.email
                ),
                status = (status ?: CaseStatus()).copy(hold = hold)
            )
        }
}

fun CaseAttr.resolve(
    user: CasesUser?,
    cases: DocumentMongoCollection = DBS.Collections.cases()
) {
    cases
        .find(_id._id())
        .firstOrNull()
        ?.toCaseHist()
        ?.apply {
            update(
                context = UpdateContext(
                    source = ".user",
                    action = "case.resolve",
                    cases = cases,
                    user = user?.email
                ),
                status = (status ?: CaseStatus()).copy(resolved = true, hold = false)
            )
        }
}

fun CaseAttr.comment(
    comment: String?,
    user: CasesUser?,
    cases: DocumentMongoCollection = DBS.Collections.cases()
) {
    cases
        .find(_id._id())
        .firstOrNull()
        ?.toCaseHist()
        ?.also { caseHist ->
            caseHist.comment = comment
            caseHist.update(
                context = UpdateContext(
                    source = ".user",
                    action = "case.comment",
                    cases = cases,
                    message = comment,
                    user = user?.email
                )
            )
        }
}