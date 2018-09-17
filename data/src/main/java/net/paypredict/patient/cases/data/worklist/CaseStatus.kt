package net.paypredict.patient.cases.data.worklist

import com.vaadin.flow.templatemodel.Encode
import net.paypredict.patient.cases.DataView
import net.paypredict.patient.cases.MetaData
import net.paypredict.patient.cases.VaadinBean
import net.paypredict.patient.cases.bson.`$set`
import net.paypredict.patient.cases.data.DBS
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
    @DataView(
        label = "Date.Time", order = 10,
        docKey = "file.date"
    )
    var date: Date?,

    @DataView(
        label = "Accession", order = 20,
        docKey = "case.Case.accessionNumber"
    )
    var accession: String?,

    @DataView(
        label = "Payer Name", order = 30,
        flexGrow = 5,
        docKey = "case.view.subscriber.payerName"
    )
    var payerName: String?,

    @DataView(
        label = "Problems", order = 40,
        docKey = "status.problems"
    )
    var problems: Int?,

    @DataView(
        label = "NPI", order = 50,
        docKey = "status.values.npi.value"
    )
    var npi: Status?,

    @DataView(
        label = "Eligibility", order = 60,
        docKey = "status.values.eligibility",
        srtKey = "status.values.eligibility.value"
    )
    var eligibility: Status?,

    @DataView(
        label = "Address", order = 70,
        docKey = "status.values.address",
        srtKey = "status.values.address.value"
    )
    var address: Status?,

    @DataView(
        label = "Expert", order = 80,
        docKey = "status.values.expert",
        srtKey = "status.values.expert.value"
    )
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
        payerName = opt("case", "view", "subscriber", "payerName"),
        problems =  opt("status", "problems"),
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


var CaseStatus.statusValue: String?
    get() {
        val case = DBS.Collections.casesRaw()
            .find(Document("_id", _id))
            .projection(Document("status.value", 1))
            .firstOrNull() ?: return null
        return case.opt<String>("status", "value")
    }
    set(value) {
        DBS.Collections.casesRaw().updateOne(
            Document("_id", _id),
            Document(`$set`, Document("status.value", value))
        )
    }

