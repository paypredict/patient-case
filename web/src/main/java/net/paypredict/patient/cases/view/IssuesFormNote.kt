package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.Text
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextArea
import net.paypredict.patient.cases.data.worklist.IssueExpert
import net.paypredict.patient.cases.data.worklist.IssuesClass

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/23/2018.
 */
class IssuesFormNote<T: IssueExpert>(private val issuesClass: IssuesClass<T>) : Composite<VerticalLayout>() {

    private class IssuesLayout<T : IssueExpert>(
        issuesClass: IssuesClass<T>,
        items: List<T>
    ) : VerticalLayout() {
        init {
            isPadding = false
            height = null

            items.forEach { item ->
                this += Div().apply {
                    style["font-weight"] = "bold"
                    this += Text(item.status?.name ?: issuesClass.caption)
                }
                this += TextArea().apply {
                    style["margin-top"] = "0"
                    width = "100%"
                    isReadOnly = true
                    value = item.text
                }
            }
        }
    }

    init {
        content.height = null
        content.isPadding = false
    }

    var value: List<T>? = null
        set(value) {
            field = value
            content.removeAll()
            val list = value ?: emptyList()
            if (list.isNotEmpty()) {
                content.add(IssuesLayout(issuesClass, list))
            }
        }
}
