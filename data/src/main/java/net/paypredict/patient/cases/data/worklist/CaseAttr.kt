package net.paypredict.patient.cases.data.worklist

import com.vaadin.flow.templatemodel.Encode
import net.paypredict.patient.cases.DataView
import net.paypredict.patient.cases.MetaData
import net.paypredict.patient.cases.VaadinBean
import net.paypredict.patient.cases.data.DateToDateTimeBeanEncoder
import net.paypredict.patient.cases.metaDataMap
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
        label = "Date.Time", order = 10,
        docKey = "file.created"
    )
    var date: Date? = null,

    @DataView(
        label = "Accession", order = 20,
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
        label = "Status", order = 100,
        docKey = "status",
        srtKey = "status.value",
        isVisible = false
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
        status = opt<Document>("status")?.toCaseStatus()
    )

@VaadinBean
data class CaseStatus(
    val checked: Boolean = false,
    val passed: Boolean = false,
    val resolved: Boolean = false,
    val timeout: Boolean = false,
    val sent: Boolean = false
) {
    val value: String
        get() = when {
            sent -> "SENT"
            timeout -> "TIMEOUT"
            resolved -> "RESOLVED"
            passed -> "PASSED"
            checked -> "CHECKED"
            else -> ""
        }
}

fun Document.toCaseStatus(): CaseStatus =
    CaseStatus(
        checked = opt("checked") ?: false,
        passed = opt("passed") ?: false,
        resolved = opt("resolved") ?: false,
        timeout = opt("timeout") ?: false,
        sent = opt("sent") ?: false
    )

fun CaseStatus.toDocument(): Document =
    doc {
        self["checked"] = checked
        self["passed"] = passed
        self["resolved"] = resolved
        self["timeout"] = timeout
        self["sent"] = sent
        self["value"] = value
    }

val CaseStatus.isCheckedOnly: Boolean
    get() = checked && !resolved && !passed && !timeout && !sent

val CaseAttr.isCheckedOnly: Boolean
    get() = status?.isCheckedOnly ?: false


fun CaseAttr.resolve(cases: DocumentMongoCollection = DBS.Collections.cases()) {
    cases
        .find(_id._id())
        .firstOrNull()
        ?.toCaseHist()
        ?.apply {
            update(
                context = UpdateContext(
                    source = ".user",
                    action = "case.resolve",
                    cases = cases
                ),
                status = (status ?: CaseStatus()).copy(resolved = true)
            )
        }
}