package net.paypredict.patient.cases.view.logs

import net.paypredict.patient.cases.mongo.*
import org.bson.Document
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.*

data class LogSumItem(
    val date: LocalDate,
    val received: Int,
    val sent: Int,
    val passed: Int,
    val resolved: Int,
    val timeout: Int
)

enum class LogSumAction(val label: String, val sentStatusField: String? = null) {
    RECEIVED("Total Received"),
    SENT("Sent Total", "sent"),
    SENT_PASSED("Sent Auto", "passed"),
    SENT_RESOLVED("Sent Manual", "resolved"),
    SENT_TIMEOUT("Sent Timeout", "timeout")
}

private val SYSTEM_ZONE_ID: ZoneId = ZoneId.systemDefault()
private val ONE_DAY: Period = Period.ofDays(1)

fun LocalDate.toSystemDate(): Date = Date.from(atStartOfDay(SYSTEM_ZONE_ID).toInstant())
fun Date.toSystemLocalDate(): LocalDate = toInstant().atZone(SYSTEM_ZONE_ID).toLocalDate()

fun ClosedRange<LocalDate>.toLogSumFilter() =
    doc {
        val min: Date = start.toSystemDate()
        val max: Date = (endInclusive + ONE_DAY).toSystemDate()
        self["time"] = doc {
            self[`$gte`] = min
            self[`$lt`] = max
        }
    }

private var isFirstTime = true

fun casesLog(): DocumentMongoCollection =
    DBS.Collections
        .casesLog()
        .also {
            if (isFirstTime) {
                isFirstTime = false
                it.createIndex(doc { self["time"] = 1 })
            }
        }

fun Document.statusIs(field: LogSumAction, vararg andNot: LogSumAction): Boolean {
    val status = opt<Document>("status") ?: return false
    fun isStatusFieldTrue(field: LogSumAction): Boolean {
        val key = field.sentStatusField ?: throw AssertionError("invalid field: $field")
        return status.opt(key) ?: false
    }
    return when (isStatusFieldTrue(field)) {
        true ->
            when {
                andNot.isEmpty() -> true
                else -> !andNot.any { isStatusFieldTrue(it) }
            }
        else -> false
    }
}