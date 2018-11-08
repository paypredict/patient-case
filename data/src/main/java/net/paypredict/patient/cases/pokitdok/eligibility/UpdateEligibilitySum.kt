package net.paypredict.patient.cases.pokitdok.eligibility

import net.paypredict.patient.cases.data.worklist.eligibility
import net.paypredict.patient.cases.data.worklist.toCaseHist
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
        val cases = DBS.Collections.cases()
        val eligibility = DBS.Collections.eligibility()
        val executorService: ExecutorService = Executors.newSingleThreadExecutor()

        val accessionById = cases.find()
            .projection(doc { doc["case.Case.accessionNumber"] = 1 })
            .mapNotNull { case ->
                case.opt<String>("case", "Case", "accessionNumber")
                    ?.let { case["_id"] as String to it }
            }
            .toMap()

        cases.find()
            .map { it.toCaseHist() }
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
