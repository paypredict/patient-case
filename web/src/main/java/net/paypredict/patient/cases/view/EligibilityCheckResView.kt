package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityCheckRes
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import org.intellij.lang.annotations.Language

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/9/2018.
 */
class EligibilityCheckResView : Composite<VerticalLayout>(), HasSize, ThemableLayout {
    var value: EligibilityCheckRes? = null
        set(new) {
            field = new
            resultDiv.rawText =
                    if (new is EligibilityCheckRes.HasResult)
                        new.result.toJson(MONGO_SHELL) else
                        ""
        }

    private val resultDiv = Div().apply {
        setSizeFull()
        style["overflow"] = "auto"
        style["font-family"] = "monospace"
    }

    private var Div.rawText: String
        get() = ""
        set(value) {
            @Language("HTML") val html =
                """<pre style="width: 100%">${value.esc}</pre>"""
            element.setProperty("innerHTML", html)
        }

    private val String.esc: String
        get() =
            this.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")

    init {
        content.isPadding = false
        content += resultDiv
        content.setFlexGrow(1.0, resultDiv)
    }

    companion object {
        private val MONGO_SHELL = JsonWriterSettings(JsonMode.SHELL, true)
    }
}
