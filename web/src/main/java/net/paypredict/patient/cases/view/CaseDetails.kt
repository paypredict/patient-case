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
@Tag("case-details")
@HtmlImport("src/case-details.html")
class CaseDetails : PolymerTemplate<CaseDetails.CaseModel>() {
    interface CaseModel : TemplateModel {
        var cs: Case?
    }

    init {
        model.cs = null
    }

    var cs: Case?
        get() = model.cs
        set(value) {
            model.cs = value
        }
}
