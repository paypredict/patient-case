package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.tabs.Tab
import com.vaadin.flow.component.tabs.Tabs
import com.vaadin.flow.data.binder.Binder
import net.paypredict.patient.cases.mongo.opt
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityCheckRes
import org.bson.Document
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/9/2018.
 */
class EligibilityCheckResView : Composite<VerticalLayout>(), HasSize, ThemableLayout {
    private var binder: Binder<Document?> = Binder()

    var value: EligibilityCheckRes? = null
        set(new) {
            field = new
            new.showRes()
        }

    class TabPage(val tab: Tab, val page: Component)

    private val tabs = Tabs().apply {
        addSelectedChangeListener {
            val tab = selectedTab
            pages.removeAll()
            tabPageList.forEach { tabPage ->
                if (tabPage.tab === tab) {
                    pages += tabPage.page
                }
            }
        }
    }
    private val pages = VerticalLayout().apply {
        isPadding = false
    }
    private val tabPageList = mutableListOf<TabPage>()

    private val warnPage = Div().apply {
        Tab("Warnings").page(this)
        setSizeFull()
        style["overflow"] = "auto"
        style["font-family"] = "monospace"
    }

    private val coveragePage = FormLayout().apply {
        Tab("Coverage").page(this)
        setSizeFull()
        val checkbox = Checkbox().apply { isReadOnly = true }
        binder
            .forField(checkbox)
            .bind(
                { it?.opt<Boolean>("data", "coverage", "active") ?: false },
                { _, _ -> }
            )
        addFormItem(checkbox, "Coverage Active?")
    }
    private val outOfPocketPage = VerticalLayout().apply {
        Tab("Out Of Pocket").page(this)
    }

    private val coinsurancePage = VerticalLayout().apply {
        Tab("Coinsurance").page(this)
    }

    private val copayPage = VerticalLayout().apply {
        Tab("Copay").page(this)
    }

    private val limitationsPage = VerticalLayout().apply {
        Tab("Limitations").page(this)
    }

    private val jsonPage = Div().apply {
        Tab("raw json").page(this)
        setSizeFull()
        style["overflow"] = "auto"
        style["font-family"] = "monospace"
    }

    private val errPage = Div().apply {
        Tab("Error").page(this)
        setSizeFull()
        style["overflow"] = "auto"
        style["font-family"] = "monospace"
    }

    private val passPages: Set<Component> = setOf(
        coveragePage, outOfPocketPage, coinsurancePage, copayPage, limitationsPage, jsonPage
    )
    private val warnPages: Set<Component> = setOf(warnPage) + passPages
    private val errPages: Set<Component> = setOf(errPage)

    private fun Tab.page(page: Component) {
        tabPageList += TabPage(this, page)
        tabs += this
    }

    private fun EligibilityCheckRes?.showRes() {
        when (this) {
            is EligibilityCheckRes.Pass -> showPass(this)
            is EligibilityCheckRes.Warn -> showWarn(this)
            is EligibilityCheckRes.Error -> showError(this)
            EligibilityCheckRes.NotAvailable -> showError(null)
            null -> showError(null)
        }
    }

    private fun Set<Component>.showPages() {
        tabPageList.forEach { it.tab.isVisible = it.page in this }
        tabs.selectedTab = tabPageList.firstOrNull { it.tab.isVisible }?.tab
    }

    private fun showPass(res: EligibilityCheckRes.Pass) {
        passPages.showPages()
        binder.readBean(res.result)
        jsonPage.rawText = res.result.toJson(JsonWriterSettings(JsonMode.SHELL, true))
    }

    private fun showWarn(res: EligibilityCheckRes.Warn) {
        warnPages.showPages()
        binder.readBean(res.result)
        warnPage.rawText = res.message + ":\n" + res.warnings.joinToString(separator = "\n") { it.message }
        jsonPage.rawText = res.result.toJson(JsonWriterSettings(JsonMode.SHELL, true))
    }

    private fun showError(res: EligibilityCheckRes.Error?) {
        errPages.showPages()
        errPage.rawText = res?.message ?: "Eligibility Not Available"
    }

    private var Div.rawText: String
        get() = ""
        set(value) {
            element.setProperty("innerHTML", "<pre>${value.esc}</pre>")
        }

    private val String.esc: String
        get() =
            this.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")

    init {
        content.isPadding = false
        content += tabs
        content += pages
        content.setFlexGrow(1.0, pages)
    }

}
