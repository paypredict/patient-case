package net.paypredict.patient.cases.view

import com.pipl.api.data.containers.Person
import com.pipl.api.data.fields.*
import com.pipl.api.search.SearchAPIError
import com.pipl.api.search.SearchAPIRequest
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.datepicker.DatePicker
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import net.paypredict.patient.cases.apis.pipl.piplApiSearchConfiguration
import net.paypredict.patient.cases.data.DBS
import net.paypredict.patient.cases.data.doc
import net.paypredict.patient.cases.data.opt
import net.paypredict.patient.cases.data.worklist.*
import net.paypredict.patient.cases.pokitdok.client.ApiException
import net.paypredict.patient.cases.pokitdok.client.EligibilityQuery
import net.paypredict.patient.cases.pokitdok.client.digest
import net.paypredict.patient.cases.pokitdok.client.query
import org.bson.Document
import java.io.IOException
import java.time.ZoneOffset
import java.util.*
import kotlin.properties.Delegates


/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
class CaseIssuesForm : Composite<Div>() {
    var value: CaseStatus? by Delegates.observable(null) { _, _: CaseStatus?, new: CaseStatus? ->
        accession.value = new?.accession ?: ""
        claim.value = new?.claim ?: ""

        val caseIssues = new?.let {
            DBS.Collections.casesIssues().find(Document("_id", it._id)).firstOrNull()?.toCaseIssues()
        }
        val patient = caseIssues?.patient
        patientFirstName.value = patient?.firstName ?: ""
        patientLastName.value = patient?.lastName ?: ""
        patientMI.value = patient?.mi ?: ""
        patientDOB.value = patient?.dobAsLocalDate

        issuesNPI.value = caseIssues?.npi
        issuesEligibility.value = caseIssues?.eligibility
        issuesAddress.value = caseIssues?.address
        issuesExpert.value = caseIssues?.expert
    }

    private val accession = TextField("Accession").apply { isReadOnly = true }
    private val claim = TextField("Claim ID").apply { isReadOnly = true }

    private val patientFirstName = TextField("Patient First Name").apply { isReadOnly = true }
    private val patientLastName = TextField("Patient Last Name").apply { isReadOnly = true }
    private val patientMI = TextField("Patient MI").apply { isReadOnly = true }
    private val patientDOB = DatePicker("Patient DOB").apply { isReadOnly = true }

    private val issuesNPI = IssuesFormGrid(IssueNPI)
    private val issuesEligibility = IssuesFormGrid(IssueEligibility) { openEligibilityDialog(it) }
    private val issuesAddress = IssuesFormGrid(IssueAddress) { openAddressDialog(it) }
    private val issuesExpert = IssuesFormNote(IssueExpert)

    init {
        content.setSizeFull()
        content.style["overflow"] = "auto"
        content += VerticalLayout().apply {
            setSizeUndefined()
            this += FormLayout().apply {
                setResponsiveSteps(
                    FormLayout.ResponsiveStep("0", 1),
                    FormLayout.ResponsiveStep("32em", 2),
                    FormLayout.ResponsiveStep("32em", 3),
                    FormLayout.ResponsiveStep("32em", 4)
                )

                this += H2("Case Issues").apply { element.setAttribute("colspan", "2") }
                this += accession
                this += claim

                this += patientLastName
                this += patientFirstName
                this += patientMI
                this += patientDOB

            }
            this += VerticalLayout(issuesNPI, issuesEligibility, issuesAddress, issuesExpert).apply {
                isPadding = false
                height = null
            }
        }
    }

    private fun openEligibilityDialog(eligibility: IssueEligibility) {
        Dialog().apply {
            width = "70vw"
            this += PatientEligibilityForm().apply {
                setSizeFull()
                isPadding = false
                value = eligibility
                checkPatientEligibility = { issue: IssueEligibility ->
                    try {
                        checkEligibility(issue)
                        close()
                    } catch (e: Throwable) {
                        showError(
                            when (e) {
                                is ApiException ->
                                    Document
                                        .parse(e.responseJson.toString())
                                        .opt<String>("data", "errors", "query")
                                        ?: e.message
                                else ->
                                    e.message
                            }
                        )
                    }
                }
            }
            open()
        }
    }

    private fun checkEligibility(issue: IssueEligibility) {
        val tradingPartnerId: String =
            issue.toTradingPartnerId()

        val query = EligibilityQuery(
            member = EligibilityQuery.Member(
                first_name = issue.subscriber!!.firstName!!,
                last_name = issue.subscriber!!.lastName!!,
                birth_date = issue.subscriber!!.dobAsLocalDate!! formatAs EligibilityQuery.Member.dateFormat,
                id = issue.subscriber!!.policyNumber!!
            ),
            provider = EligibilityQuery.Provider(
                organization_name = "SAGIS, PLLC",
                npi = "1548549066"
            ),
            trading_partner_id = tradingPartnerId
        )

        val digest = query.digest()
        val collection = DBS.Collections.eligibility()
        collection
            .find(doc { doc["_id"] = digest }).firstOrNull()
            ?: query.query { Document.parse(it.readText()) }.let { response ->
                doc {
                    doc["_id"] = digest
                    doc["data"] = response["data"]
                    doc["meta"] = response["meta"]
                }.also {
                    collection.insertOne(it)
                }

            }
    }

    fun IssueEligibility.toTradingPartnerId(): String {
        val insurancePayerId = insurance?.payerId ?: throw AssertionError("insurance payerId is required")
        val tradingPartners = DBS.Collections.tradingPartners()
        return tradingPartners
            .find(doc { doc["data.payer_id"] = insurancePayerId })
            .firstOrNull()
            ?.opt<String>("_id")
            ?: tradingPartners
                .find(doc { doc["custom.payer_id"] = insurancePayerId })
                .firstOrNull()
                ?.opt<String>("_id")
            ?: throw IOException("insurance payerId $insurancePayerId not found in tradingPartners")
    }

    private fun openAddressDialog(address: IssueAddress) {
        Dialog().apply {
            width = "70vw"
            this += AddressForm().apply {
                setSizeFull()
                isPadding = false
                value = address
                checkPatientAddress = { issue: IssueAddress ->
                    try {
                        checkAddress(issue)
                        close()
                    } catch (e: Throwable) {
                        showError(
                            when (e) {
                                is SearchAPIError -> {
                                    System.err.println(e.json)
                                    e.error
                                }
                                else -> e.message
                            }
                        )
                    }
                }
            }
            open()
        }
    }

    private fun checkAddress(issue: IssueAddress) {
        val apiRequest = SearchAPIRequest(Person(
            mutableListOf<Field>().also { fields ->
                fields += Address.Builder()
                    .country("US")
                    .state(issue.state)
                    .city(issue.city)
                    .zipCode(issue.zip)
                    .street(issue.address1)
                    .build()
                issue.person?.also { person ->
                    fields += Name.Builder()
                        .apply {
                            person.firstName?.also { first(it) }
                            person.lastName?.also { last(it) }
                            person.mi?.also { middle(it) }
                        }
                        .build()
                    person.dobAsLocalDate?.also { dob ->
                        fields += DOB(DateRange().apply {
                            start = Date.from(
                                dob
                                    .withMonth(1)
                                    .withDayOfMonth(1)
                                    .atStartOfDay(ZoneOffset.UTC)
                                    .toInstant()
                            )
                            end = Date.from(
                                dob
                                    .withMonth(12)
                                    .withDayOfMonth(31)
                                    .atStartOfDay(ZoneOffset.UTC)
                                    .toInstant()
                            )
                        })
                    }
                }
            }
        ),
            piplApiSearchConfiguration
        )
        val response = apiRequest.send()
        println(response.json)
    }

    private fun showError(error: String?) {
        Dialog().apply {
            this += VerticalLayout().apply {
                this += H2("API Call Error")
                this += H3(error)
            }
            open()
        }
    }
}