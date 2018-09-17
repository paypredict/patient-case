package net.paypredict.patient.cases.data

/**
 *
 *
 * Created by alexei.vylegzhanin@gmail.com on 9/18/2018.
 */

import net.paypredict.patient.cases.data.worklist.updateCasesIssues
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener
import javax.servlet.annotation.WebListener
import kotlin.concurrent.thread

@WebListener
class UpdateCasesIssuesStarter : ServletContextListener {
    private lateinit var sce: ServletContextEvent
    private val thread = thread(start = false, name = "UpdateCasesIssues Starter") {
        val currentThread = Thread.currentThread()
        while (!currentThread.isInterrupted) {
            try {
                updateCasesIssues { currentThread.isInterrupted }
                Thread.sleep(30000)
            } catch (e: Throwable) {
                if (e is InterruptedException) break
                sce.servletContext.log("updateCasesIssues error: " + e.message, e)
            }
        }
    }

    override fun contextInitialized(sce: ServletContextEvent) {
        this.sce = sce
        thread.start()
    }

    override fun contextDestroyed(sce: ServletContextEvent) {
        thread.interrupt()
        thread.join(3000)
    }
}
