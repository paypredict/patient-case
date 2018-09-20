package net.paypredict.patient.cases.data

/**
 * Created by alexei.vylegzhanin@gmail.com on 9/18/2018.
 */

import net.paypredict.patient.cases.data.worklist.updateCasesIssues
import java.util.logging.Level
import java.util.logging.Logger
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener
import javax.servlet.annotation.WebListener
import kotlin.concurrent.thread

@WebListener
class UpdateCasesIssuesStarter : ServletContextListener {
    private val log: Logger by lazy { Logger.getLogger(UpdateCasesIssuesStarter::class.qualifiedName) }
    private val thread = thread(start = false, name = "UpdateCasesIssues Starter") {
        val currentThread = Thread.currentThread()
        while (!currentThread.isInterrupted) {
            try {
                val t1 = System.currentTimeMillis()
                updateCasesIssues { currentThread.isInterrupted }
                val t2 = System.currentTimeMillis()
                if (t2 - t1 > 2000)
                    log.info("updateCasesIssues has finished in ${t2 - t1}ms")
                Thread.sleep(30000)
            } catch (e: Throwable) {
                if (e is InterruptedException) break
                log.log(Level.SEVERE, "updateCasesIssues error", e)
            }
        }
    }

    override fun contextInitialized(sce: ServletContextEvent) {
        thread.start()
    }

    override fun contextDestroyed(sce: ServletContextEvent) {
        thread.interrupt()
        thread.join(3000)
    }
}
