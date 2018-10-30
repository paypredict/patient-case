package net.paypredict.patient.cases.pokitdok.eligibility

import net.paypredict.patient.cases.data.worklist.toCaseIssue
import net.paypredict.patient.cases.mongo.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 10/30/2018.
 */
object UpdateEligibilitySum {
    @JvmStatic
    fun main(args: Array<String>) {
        val casesRaw = DBS.Collections.casesRaw()
        val casesIssues = DBS.Collections.casesIssues()
        val eligibility = DBS.Collections.eligibility()
        val executorService: ExecutorService = Executors.newSingleThreadExecutor()

        val accessionById = casesRaw.find()
            .projection(doc { doc["case.Case.accessionNumber"] = 1 })
            .mapNotNull { case ->
                case.opt<String>("case", "Case", "accessionNumber")
                    ?.let { case["_id"] as String to it }
            }
            .toMap()

        casesIssues.find()
            .map { it.toCaseIssue() }
            .toList()
            .forEach { casesIssue ->
                casesIssue.eligibility
                    .filterNot { it.eligibility.isNullOrBlank() }
                    .forEach { issue ->
                        issue.eligibility?._id()?.also { filter ->
                            val caseId = casesIssue._id
                            val caseAcn = caseId?.let { accessionById[it] }
                            val zmPayerId = issue.insurance?.zmPayerId
                            println("$caseAcn / $caseId / $zmPayerId")
                            executorService.submit {
                                eligibility.upsertOne(
                                    filter,
                                    doc {
                                        doc[`$set`] = doc {
                                            doc["sum.caseId"] = caseId
                                            doc["sum.caseAcn"] = caseAcn
                                            doc["sum.zmPayerId"] = zmPayerId
                                        }
                                    }
                                )
                            }
                        }
                    }
            }

        executorService.shutdown()

        while (true) {
            if (executorService.awaitTermination(1, TimeUnit.SECONDS)) exitProcess(0)
            println("updating...")
        }
    }
}
