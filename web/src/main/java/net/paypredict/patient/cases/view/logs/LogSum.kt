package net.paypredict.patient.cases.view.logs

import net.paypredict.patient.cases.mongo.*
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.*

data class LogSumItem(
    val date: LocalDate,
    val received: Int,
    val sent: Int,
    val resolved: Int,
    val timeout: Int
)

sealed class LogSumAction {
    object RECEIVED : LogSumAction()
    object SENT : LogSumAction()
    object RESOLVED : LogSumAction()
    object TIMEOUT : LogSumAction()
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
