package net.paypredict.patient.cases.data.worklist

import com.smartystreets.api.exceptions.BadRequestException
import com.smartystreets.api.exceptions.SmartyException
import com.smartystreets.api.us_street.Lookup
import com.smartystreets.api.us_street.MatchType
import net.paypredict.patient.cases.CasesUser
import net.paypredict.patient.cases.apis.npiregistry.NpiRegistry
import net.paypredict.patient.cases.apis.npiregistry.NpiRegistryException
import net.paypredict.patient.cases.apis.smartystreets.FootNote
import net.paypredict.patient.cases.apis.smartystreets.UsStreet
import net.paypredict.patient.cases.apis.smartystreets.footNoteSet
import net.paypredict.patient.cases.Import
import net.paypredict.patient.cases.created
import net.paypredict.patient.cases.data.cases.*
import net.paypredict.patient.cases.mongo.*
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityCheckRes
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityChecker
import org.bson.Document
import java.io.File
import java.time.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.withLock

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/16/2018.
 */

fun updateCasesIssues(isInterrupted: () -> Boolean = { false }) {
    val cases = DBS.Collections.cases()
    cases.importCases(isInterrupted = isInterrupted)
    cases.checkCases(isInterrupted = isInterrupted)
    cases.markTimeoutCases(isInterrupted = isInterrupted)
    cases.sendCases(isInterrupted = isInterrupted)
}

private object ImportCasesAttempts {
    const val MAX: Int = 10
    private val lock = ReentrantLock()
    private val attemptsByFilePath = mutableMapOf<String, AtomicInteger>()

    operator fun get(file: File): Int = lock.withLock {
        attemptsByFilePath[file.path]?.get() ?: 0
    }

    inline operator fun invoke(file: File, onFirstError: () -> Unit) = lock.withLock {
        attemptsByFilePath.getOrPut(file.path) {
            onFirstError()
            AtomicInteger(0)
        }.incrementAndGet()
    }
}

private fun DocumentMongoCollection.importCases(isInterrupted: () -> Boolean) {
    val timeout: Long = Import.Conf.timeOutDaysImport.toDaysBackLDT().toInstant().toEpochMilli()
    for (file in ordersSrcDir.walk()) {
        if (isInterrupted()) break
        if (!file.isFile) continue
        try {
            if (file.created() < timeout) continue
            if (ImportCasesAttempts[file] > ImportCasesAttempts.MAX) continue
            Import.importXmlFile(
                xmlFile = file,
                cases = this,
                skipByNameAndTime = true,
                override = false,
                onNewFile = { file.archiveCaseFile(it, BackupMode.SRC) }
            )
        } catch (e: Throwable) {
            ImportCasesAttempts(file) {
                Logger
                    .getLogger(Import::class.qualifiedName)
                    .log(Level.WARNING, "importXmlFile $file error", e)
            }
            continue
        }
    }
}

private fun DocumentMongoCollection.checkCases(
    user: CasesUser? = null,
    isInterrupted: () -> Boolean
) {
    val items: List<Document> = find(doc { self["status.checked"] = doc { self[`$exists`] = false } })
        .projection(doc { self["_id"] = 1 })
        .toList()
    val usStreet = UsStreet()
    val payerLookup = PayerLookup()
    for (item in items) {
        val case = find(item).firstOrNull() ?: continue
        val issueCheckerAuto =
            IssueCheckerAuto(
                usStreet = usStreet,
                payerLookup = payerLookup,
                cases = this,
                case = case,
                user = user
            )
        issueCheckerAuto.check()
        if (isInterrupted()) break
    }
}

private fun Int.toDaysBackLDT(): LocalDateTime =
    LocalDateTime.now() - Duration.ofDays(toLong())

private fun LocalDateTime.toInstant(): Instant =
    atZone(ZoneId.systemDefault())
        .toInstant()

private fun DocumentMongoCollection.markTimeoutCases(
    user: CasesUser? = null,
    isInterrupted: () -> Boolean
) {
    val timeout: Date = Date.from(Import.Conf.timeOutDaysMark.toDaysBackLDT().toInstant())
    val filter = doc {
        self["status.timeout"] = false
        self["file.created"] = doc { self[`$lt`] = timeout }
    }
    val items: List<Document> =
        find(filter)
            .projection(doc { self["_id"] = 1 })
            .toList()

    for (item in items) {
        find(item)
            .firstOrNull()
            ?.toCaseHist()
            ?.run {
                update(
                    context = UpdateContext(
                        source = ".system",
                        action = "case.markTimeout",
                        cases = this@markTimeoutCases,
                        message = "timeout",
                        user = user?.email
                    ),
                    status = (status ?: CaseStatus()).copy(timeout = true)
                )
            }
        if (isInterrupted()) break
    }
}

private fun DocumentMongoCollection.sendCases(
    user: CasesUser? = null,
    isInterrupted: () -> Boolean
) {
    val statusToSend: List<String> =
        listOf(
            // CaseStatus.Sum.PASSED, TODO: add for auto-send mode
            CaseStatus.Sum.RESOLVED,
            CaseStatus.Sum.TIMEOUT
        ).map { it.name }

    val filter = doc {
        self["status.sent"] = false
        self["status.value"] = doc { self[`$in`] = statusToSend }
    }

    val items: List<Document> = this
        .find(filter)
        .projection(doc { self["_id"] = 1 })
        .toList()

    for (item in items) {
        this
            .find(item)
            .firstOrNull()
            ?.toCaseHist()
            ?.safeCall(action = "case.send", user = user) {
                send(cases = this@sendCases, user = user)
            }
        if (isInterrupted()) break
    }
}

private inline fun CaseHist.safeCall(
    action: String,
    user: CasesUser? = null,
    function: CaseHist.() -> Unit
) {
    try {
        function()
    } catch (e: Exception) {
        Logger
            .getLogger("CaseHist.$action")
            .log(Level.WARNING, "error on $_id ($accession) $action: ${e.message}")

        updateStatus(
            context = UpdateContext(
                source = if (user == null) ".system" else ".user",
                action = action,
                message = e.javaClass.name + ": " + e.message,
                user = user?.email
            ),
            logLevel = LogLevel.ERROR,
            status = (status ?: CaseStatus()).copy(error = true)
        )
    }
}

private fun CaseHist.send(
    cases: DocumentMongoCollection,
    user: CasesUser? = null
) {
    if (status?.sent == true) return
    createOutXml()
    update(
        context = UpdateContext(
            source = ".system",
            action = "case.send",
            cases = cases,
            message = "sent",
            user = user?.email
        ),
        status = (status ?: CaseStatus()).copy(sent = true)
    )
}

class CheckingException(override val message: String, var status: IssuesStatus? = null) : Exception()

open class IssueChecker(
    private val usStreet: UsStreet = UsStreet(),
    protected val payerLookup: PayerLookup = PayerLookup(),
    protected val cases: DocumentMongoCollection = DBS.Collections.cases()
) {

    fun checkIssueAddressAndUpdateStatus(issue: IssueAddress) {
        checkIssueAddress(issue)
    }

    protected fun checkIssueAddress(issue: IssueAddress) {
        issue.status = IssueAddress.Status.Unchecked()
        issue.footnotes = null

        val lookup = Lookup().apply {
            match = MatchType.RANGE
            street = issue.address1
            street2 = issue.address2
            city = issue.city
            state = issue.state
            zipCode = issue.zip
        }

        try {
            usStreet.send(lookup)
        } catch (x: Throwable) {
            when (x) {
                is SmartyException ->
                    throw CheckingException("smartyStreets api error: " + x.message)
                else -> {
                    x.printStackTrace()
                    throw CheckingException(
                        "smartyStreets api processing error "
                                + x.javaClass.name + ": "
                                + x.message
                    )
                }
            }
        }

        val candidate = lookup.result.firstOrNull()
            ?: throw throw CheckingException("Address not found by smartyStreets api")

        issue.footnotes = candidate.analysis.footnotes

        issue.address1 = candidate.deliveryLine1
        issue.address2 = candidate.deliveryLine2
        val components = candidate.components
            ?: throw CheckingException("candidate.components not found in smartyStreets api response")
        issue.city = components.cityName
        issue.state = components.state
        issue.zip = components.zipCode + components.plus4Code.let {
            when {
                it.isNullOrBlank() -> ""
                else -> "-$it"
            }
        }

        val maxFootNote = candidate.analysis.footNoteSet.asSequence().maxBy { it.level }
        return when (maxFootNote?.level) {
            null -> {
                issue.status = IssueAddress.Status.Confirmed(
                    footnotes = candidate.analysis.footnotes
                )
            }
            FootNote.Level.ERROR -> {
                issue.status = IssueAddress.Status.Error(
                    error = maxFootNote.label,
                    message = maxFootNote.note,
                    footnotes = candidate.analysis.footnotes
                )
            }
            FootNote.Level.WARNING -> {
                issue.status = IssueAddress.Status.Corrected(
                    footnotes = candidate.analysis.footnotes
                )
            }
            FootNote.Level.INFO -> {
                issue.status = IssueAddress.Status.Confirmed(
                    footnotes = candidate.analysis.footnotes
                )
            }
        }
    }
}


internal class IssueCheckerAuto(
    usStreet: UsStreet = UsStreet(),
    payerLookup: PayerLookup = PayerLookup(),
    cases: DocumentMongoCollection = DBS.Collections.cases(),
    private val case: Document,
    private val user: CasesUser? = null
) : IssueChecker(usStreet, payerLookup, cases) {

    private lateinit var caseHist: CaseHist
    private var passed: Boolean = true

    fun check(): Boolean {
        caseHist = case.toCaseHist()
        passed = true
        val status =
            (caseHist.status ?: CaseStatus()).copy(
                checked = false,
                passed = false
            )

        val hasResultByResponsibilityMap: MutableMap<String, EligibilityCheckRes.HasResult> = mutableMapOf()

        checkNPI()
        checkSubscriber(caseHist.patient) { hasResult ->
            responsibility?.also { hasResultByResponsibilityMap[it] = hasResult }
        }
        checkAddress(
            ResponsibilityOrder.values()
                .asSequence()
                .mapNotNull { hasResultByResponsibilityMap[it.name]?.findSubscriberAddress() }
                .firstOrNull())

        val statusRes =
            status.copy(
                checked = true,
                passed = passed
            )
        caseHist.update(
            UpdateContext(
                source = ".system",
                action = "case.check",
                cases = cases,
                message = statusRes.value,
                user = user?.email
            ),
            status = statusRes
        )
        return passed
    }

    private fun checkNPI() {
        val provider = case<Document>("case", "Case", "OrderingProvider", "Provider")
        if (provider == null) {
            caseHist = caseHist.copy(npi = listOf(IssueNPI(status = IssueNPI.Status.Unchecked)))
        } else {
            val npi = provider<String>("npi")
            val originalNPI = IssueNPI(
                status = IssueNPI.Status.Original,
                npi = npi,
                name = Person(
                    firstName = provider<String>("firstName"),
                    lastName = provider<String>("lastName"),
                    mi = provider<String>("middleInitials")
                )
            )
            val apiNPI = originalNPI.copy(
                status = IssueNPI.Status.Unchecked
            )
            try {
                if (npi == null) throw CheckingException("Case Provider npi is null")

                val npiRes = try {
                    NpiRegistry.find(npi)
                } catch (e: NpiRegistryException) {
                    throw CheckingException(e.message)
                }

                val results = npiRes<List<*>>("results")
                    ?: throw CheckingException("Invalid API response: results in null")
                if (results.size != 1)
                    throw CheckingException("Invalid API response: results.size != 1 (${results.size})")

                val res = results.firstOrNull() as? Document
                    ?: throw CheckingException("Invalid API response: results[0] isn't Document")

                val basic = res<Document>("basic")

                apiNPI.name = Person(
                    firstName = basic<String>("first_name"),
                    lastName = basic<String>("last_name"),
                    mi = basic<String>("middle_name")
                )
                apiNPI.taxonomies = res<List<*>>("taxonomies")
                    ?.asSequence()
                    ?.filterIsInstance<Document>()
                    ?.map { it.toTaxonomy() }
                    ?.toList()
                        ?: emptyList()

                val status = when (apiNPI.nameEquals(originalNPI)) {
                    true -> IssueNPI.Status.Confirmed
                    false -> IssueNPI.Status.Corrected
                }
                apiNPI.status = status

            } catch (e: CheckingException) {
                val status =
                    e.status as? IssueNPI.Status
                        ?: IssueNPI.Status.Error("Checking Error", e.message)
                apiNPI.status = status
            }
            if (apiNPI.status?.passed != true) {
                passed = false
            }
            caseHist = caseHist.copy(npi = listOf(originalNPI, apiNPI))
        }
    }

    private fun checkSubscriber(patient: Person?, onHasResult: OnHasResult) {
        val issueEligibilityList = case.toSubscriberList().map {
            IssueEligibility(
                status = IssueEligibility.Status.Original,
                origin = "casesRaw",
                responsibility = it<String>("responsibilityCode"),
                insurance = it.toInsurance(),
                subscriber = it.toSubscriber(),
                subscriberRaw = it.toSubscriberRaw()
            )
        }
        if (issueEligibilityList.isNotEmpty()) {
            val eligibilityCheckContext = EligibilityCheckContext(
                payerLookup = payerLookup,
                patient = patient,
                onHasResult = onHasResult,
                newCasesLog = { caseHist.toCasesLog() })
            val checkedEligibility =
                issueEligibilityList.map { it.checkEligibility(eligibilityCheckContext) }
            if (checkedEligibility.any { it.status?.passed != true }) {
                passed = false
            }
            caseHist = caseHist.copy().also {
                it.eligibility = issueEligibilityList + checkedEligibility
            }
        } else {
            caseHist = caseHist.copy().also {
                it.eligibility = listOf(
                    IssueEligibility(
                        status = IssueEligibility.Status.Missing,
                        origin = "checking",
                        responsibility = ResponsibilityOrder.Primary.name
                    )
                )
            }
            passed = false
        }
    }

    private fun checkAddress(subscriberAddress: IssueAddress?) {
        val person = case.findPatient()
        val history = mutableListOf<IssueAddress>()
        var issue = IssueAddress(status = IssueAddress.Status.Unchecked())
        try {
            if (person == null) throw CheckingException("Case Subscriber and Patient is null")
            issue.person = Person(
                firstName = person("firstName"),
                lastName = person("organizationNameOrLastName") ?: person("firstName"),
                mi = person("middleInitial"),
                gender = person("gender"),
                dob = person("dob") ?: person("dateOfBirth")
            )
            issue.address1 = person("address1")
            issue.address2 = person("address2")
            issue.zip = person("zip")
            issue.city = person("city")
            issue.state = person("state")

            history += issue.copy(status = IssueAddress.Status.Original())

            var hasProblems: Boolean
            try {
                if (issue.address1.isNullOrBlank()) throw CheckingException("Address not found")
                checkIssueAddress(issue)
                hasProblems = issue.status?.passed == false
            } catch (e: CheckingException) {
                hasProblems = true
                if (subscriberAddress == null) throw e
                history += issue.copy(status = e.toStatus(), error = e.message)
            }

            if (hasProblems && subscriberAddress != null) {
                history += subscriberAddress.copy(status = IssueAddress.Status.Corrected(subscriberAddress.footnotes))
                issue = subscriberAddress
                checkIssueAddress(issue)
            }

        } catch (x: Throwable) {
            val e = when (x) {
                is BadRequestException -> CheckingException("BadRequest: " + x.message)
                is CheckingException -> x
                else -> throw x
            }
            val status = e.toStatus()
            issue.status = status
            issue.error = e.message
        }
        if (issue.status?.passed != true) {
            passed = false
        }
        caseHist = caseHist.copy(address = history + issue)
    }

    private fun CheckingException.toStatus(): IssueAddress.Status =
        status as? IssueAddress.Status
            ?: IssueAddress.Status.Error("Checking Error", message)

    companion object {
        private fun Document.toSubscriberList(): List<Document> =
            opt<List<*>>("case", "Case", "SubscriberDetails", "Subscriber")
                ?.asSequence()
                ?.filterIsInstance<Document>()
                ?.toList()
                ?: emptyList()

        private fun Document.findPatient(): Document? {
            return opt<Document>("case", "Case", "Patient")
        }

        private fun Document.toSubscriber(): Subscriber =
            Subscriber(
                firstName = opt("firstName"),
                lastName = opt("organizationNameOrLastName"),
                mi = opt("middleInitial"),
                gender = opt("gender"),
                dob = opt("dob"),
                groupName = opt("groupOrPlanName"),
                groupId = opt("groupOrPlanNumber"),
                relationshipCode = opt("relationshipCode"),
                policyNumber = opt("subscriberPolicyNumber")
            )

        private fun Document.toInsurance(): Insurance =
            Insurance(
                typeCode = opt("insuranceTypeCode"),
                payerId = opt("payerId"),
                planCode = opt("planCode"),
                payerName = opt("payerName"),
                zmPayerId = opt("zmPayerId"),
                zmPayerName = opt("zmPayerName")
            )
    }

}

fun EligibilityCheckRes.HasResult.findSubscriberAddress(): IssueAddress? {
    val address = result.opt<Document>("data", "subscriber", "address")
        ?: result.opt<Document>("data", "dependent", "address")
        ?: return null
    val lines = address<List<*>>("address_lines")
        ?.filterIsInstance<String>() ?: return null
    return IssueAddress(
        address1 = lines.getOrNull(0) ?: return null,
        address2 = lines.getOrNull(1),
        zip = address("zipcode") ?: return null,
        city = address("city") ?: return null,
        state = address("state") ?: return null
    )
}

typealias OnHasResult = IssueEligibility.(EligibilityCheckRes.HasResult) -> Unit

class EligibilityCheckContext(
    val payerLookup: PayerLookup,
    val patient: Person? = null,
    val onHasResult: OnHasResult? = null,
    val newCasesLog: () -> CasesLog
)

fun IssueEligibility.checkEligibility(context: EligibilityCheckContext): IssueEligibility =
    deepCopy().apply {
        var isPayerCheckable = false
        insurance?.run {
            val payer: PayerId? = payerName?.let { context.payerLookup[it] }
            zmPayerId = payer?._id
            zmPayerName = payer?.payerName
            if (payer?.checkable == true) {
                isPayerCheckable = true
            }
        }

        var isSubscriberCheckable = false
        subscriber?.run {
            mergeFromPatient(context)
            isSubscriberCheckable =
                    !policyNumber.isNullOrBlank() &&
                    !firstName.isNullOrBlank() &&
                    !lastName.isNullOrBlank()
        }

        val checkable: Boolean = isPayerCheckable && isSubscriberCheckable
        status = when {
            checkable -> {
                val checkRes = EligibilityChecker(this, context.newCasesLog).check()
                if (checkRes is EligibilityCheckRes.HasResult) {
                    eligibility = checkRes.id
                    subscriber?.run {
                        checkRes.result.opt<Document>("data", "subscriber")
                            ?.also { res ->
                                res<String>("first_name")?.ifValid { firstName = it }
                                res<String>("last_name")?.ifValid { lastName = it }
                                res<String>("middle_name")?.ifValid { mi = it }
                                res<String>("gender")?.ifValid("Male", "Female") {
                                    gender = it.capitalize()
                                }
                                res<String>("birth_date")?.ifValid {
                                    dob = it.convertDateTime(
                                        EligibilityCheckRes.dateFormat,
                                        Person.dateFormat
                                    )
                                }
                            }
                    }
                    context.onHasResult?.invoke(this@checkEligibility, checkRes)
                } else {
                    eligibility = null
                }

                when (checkRes) {
                    is EligibilityCheckRes.Pass -> IssueEligibility.Status.Confirmed
                    is EligibilityCheckRes.Warn -> IssueEligibility.Status.Problem(
                        checkRes.message, checkRes.warnings.joinToString { it.message }
                    )
                    EligibilityCheckRes.NotAvailable -> IssueEligibility.Status.NotAvailable
                    is EligibilityCheckRes.Error -> IssueEligibility.Status.Problem(
                        "Checking Error", checkRes.message
                    )
                }
            }
            else -> IssueEligibility.Status.Unchecked
        }
    }

private fun String.ifValid(vararg allowed: String, action: (String) -> Unit) {
    if (isBlank()) return
    if (allowed.isEmpty() || allowed.any { it.equals(this, ignoreCase = true) })
        action(this)
}

private val reasonablePatientsDobYearsRange = 1901..LocalDate.now().year

private fun Subscriber.mergeFromPatient(context: EligibilityCheckContext) {
    context.patient?.also { patient ->
        patient.gender?.also {
            when (it) {
                "Male", "Female" -> gender = it
            }
        }
        patient.dobAsLocalDate?.also {
            when (it.year) {
                in reasonablePatientsDobYearsRange -> dob = patient.dob
            }
        }
        when (relationshipCode) {
            "SEL", "UNK" -> {
                if (firstName.isNullOrBlank()) firstName = patient.firstName
                if (lastName.isNullOrBlank()) lastName = patient.lastName
                if (mi.isNullOrBlank()) mi = patient.mi
            }
        }
    }
}

fun Document.casePatient(): Person? =
    opt<Document>("case", "Case", "Patient")
        ?.let { patient ->
            Person(
                firstName = patient("firstName"),
                lastName = patient("lastName"),
                mi = patient("middleInitials"),
                gender = patient("gender"),
                dob = patient("dateOfBirth")
            )
        }