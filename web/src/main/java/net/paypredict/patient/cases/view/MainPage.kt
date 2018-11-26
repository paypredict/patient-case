package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.UI
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.*

@Route("")
@PageTitle("Loading...")
class MainPage : Composite<VerticalLayout>(), BeforeEnterObserver {
    override fun beforeEnter(event: BeforeEnterEvent) {
        val ui: UI = event.ui
        val url: String = ui.router.getUrl(WorkListPage::class.java)
        ui.page.executeJavaScript("""window.location = "$url";""")
    }
}
