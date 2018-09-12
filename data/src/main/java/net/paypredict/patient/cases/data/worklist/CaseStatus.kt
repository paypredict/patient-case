package net.paypredict.patient.cases.data.worklist

import com.vaadin.flow.templatemodel.Encode
import net.paypredict.patient.cases.DataView
import net.paypredict.patient.cases.MetaData
import net.paypredict.patient.cases.VaadinBean
import net.paypredict.patient.cases.data.DateToDateTimeBeanEncoder
import net.paypredict.patient.cases.data.doc
import net.paypredict.patient.cases.data.opt
import net.paypredict.patient.cases.metaDataMap
import org.bson.Document
import java.util.*

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/19/2018.
 */
@VaadinBean
data class CaseStatus(
    @DataView("_id", isVisible = false)
    val _id: String,

    @set:Encode(DateToDateTimeBeanEncoder::class)
    @DataView("Date.Time", order = 10, docKey = "file.date")
    var date: Date?,

    @DataView("Accession", order = 20)
    var accession: String?,

    @DataView("Claim", order = 30)
    var claim: String?,

    @DataView("NPI", order = 40)
    var npi: Status?,

    @DataView("Eligibility", order = 50)
    var eligibility: Status?,

    @DataView("Address", order = 60)
    var address: Status?,

    @DataView("Expert", order = 70)
    var expert: Status?
)

val CASE_STATUS_META_DATA_MAP: Map<String, MetaData<CaseStatus>> by lazy { metaDataMap<CaseStatus>() }

@VaadinBean
data class Status(
    var value: String?,
    var description: String? = null
)

fun Document.toCaseStatus(): CaseStatus =
    CaseStatus(
        _id = get("_id").toString(),
        date = opt<Date>("file", "date"),
        accession = opt("case", "Case", "accessionNumber"),
        claim = opt("case", "Case", "SuperBillDetails", "claimNumber"),
        npi = opt<Document>("status", "values", "npi")?.toStatus(),
        eligibility = opt<Document>("status", "values", "eligibility")?.toStatus(),
        address = opt<Document>("status", "values", "address")?.toStatus(),
        expert = opt<Document>("status", "values", "expert")?.toStatus()
    )

fun Document.toStatus(): Status =
    Status(
        value = opt("value"),
        description = opt("description")
    )

fun Status.toDocument(): Document = doc {
    doc["value"] = value
    doc["description"] = description
}
