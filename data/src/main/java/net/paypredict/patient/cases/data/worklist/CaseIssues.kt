package net.paypredict.patient.cases.data.worklist

import com.vaadin.flow.templatemodel.Encode
import net.paypredict.patient.cases.*
import net.paypredict.patient.cases.data.DateToDateTimeBeanEncoder
import net.paypredict.patient.cases.data.opt
import org.bson.Document
import java.util.*

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/19/2018.
 */

@VaadinBean
data class CaseIssues(
    @DataView("_id", isVisible = false)
    val _id: String? = null,

    @set:Encode(DateToDateTimeBeanEncoder::class)
    @DataView("Date.Time", order = 10)
    var time: Date? = null,

    @DataView("NPI", order = 20)
    var npi: List<IssueNPI> = emptyList(),

    @DataView("Eligibility", order = 30)
    var eligibility: List<IssueEligibility> = emptyList(),

    @DataView("Address", order = 40)
    var address: List<IssueAddress> = emptyList()
)

interface IssuesStatus {
    var status: String?
}

interface IssuesClass<T: IssuesStatus> {
    val caption: String
    val beanType: Class<T>
}

@VaadinBean
data class IssueNPI(
    @DataView("Status", order = 10)
    override var status: String?,

    @DataView("NPI", order = 20)
    var npi: String?,

    @DataView("First Name", order = 30)
    var firstName: String?,

    @DataView("Last Name", order = 40)
    var lastName: String?
) : IssuesStatus {
    companion object : IssuesClass<IssueNPI> {
        override val caption = "Physician NPI"
        override val beanType = IssueNPI::class.java
    }
}

@VaadinBean
data class IssueEligibility(
    @DataView("Status", order = 10)
    override var status: String?,

    @DataView("Insurance", order = 20)
    var insurance: String?,

    @DataView("Member ID", order = 30)
    var memberId: String?,

    @DataView("Verification", order = 40)
    var verification: String?
) : IssuesStatus {
    companion object : IssuesClass<IssueEligibility> {
        override val caption = "Patient Eligibility"
        override val beanType = IssueEligibility::class.java
    }
}

@VaadinBean
data class IssueAddress(
    @DataView("Status", order = 10)
    override var status: String?,

    @DataView("Address 1", order = 20)
    var address1: String?,

    @DataView("Address 2", order = 30)
    var address2: String?,

    @DataView("ZIP", order = 40)
    var zip: String?,

    @DataView("City", order = 50)
    var city: String?,

    @DataView("State", order = 60)
    var state: String?
) : IssuesStatus {
    companion object : IssuesClass<IssueAddress> {
        override val caption = "Patient Address"
        override val beanType = IssueAddress::class.java
    }
}

fun Document.toCaseIssues(): CaseIssues =
    CaseIssues(
        _id = get("_id").toString(),
        time = opt<Date>("time"),
        npi = opt<List<*>>("issue", "npi")
            ?.filterIsInstance<Document>()
            ?.map { it.toIssueNPI() }
            ?: emptyList(),
        eligibility = opt<List<*>>("issue", "eligibility")
            ?.filterIsInstance<Document>()
            ?.map { it.toIssueEligibility() }
            ?: emptyList(),
        address = opt<List<*>>("issue", "address")
            ?.filterIsInstance<Document>()
            ?.map { it.toIssueAddress() }
            ?: emptyList()
    )

private fun Document.toIssueNPI(): IssueNPI =
    IssueNPI(
        status = opt<String>("status"),
        npi = opt<String>("npi"),
        firstName = opt<String>("firstName"),
        lastName = opt<String>("lastName")
    )

private fun Document.toIssueEligibility(): IssueEligibility =
    IssueEligibility(
        status = opt<String>("status"),
        insurance = opt<String>("insurance"),
        memberId = opt<String>("memberId"),
        verification = opt<String>("verification")
    )

private fun Document.toIssueAddress(): IssueAddress =
    IssueAddress(
        status = opt<String>("status"),
        address1 = opt<String>("address1"),
        address2 = opt<String>("address2"),
        zip = opt<String>("zip"),
        city = opt<String>("city"),
        state = opt<String>("state")
    )
