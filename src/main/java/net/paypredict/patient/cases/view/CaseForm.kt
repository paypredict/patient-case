package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Tag
import com.vaadin.flow.component.dependency.HtmlImport
import com.vaadin.flow.component.polymertemplate.PolymerTemplate
import com.vaadin.flow.templatemodel.TemplateModel
import net.paypredict.patient.cases.data.Case

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
@Tag("case-form")
@HtmlImport("src/case-form.html")
class CaseForm : PolymerTemplate<CaseForm.CaseModel>() {
    interface CaseModel : TemplateModel {
        var value: Case?
    }

    init {
        model.value = null
    }

    var value: Case?
        get() = model.value
        set(value) {
            model.value = value
        }
}
