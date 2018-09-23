package net.paypredict.patient.cases.html

import com.vaadin.flow.component.HtmlContainer
import com.vaadin.flow.component.PropertyDescriptors
import com.vaadin.flow.component.Tag
import com.vaadin.flow.component.dependency.HtmlImport
import com.vaadin.flow.server.AbstractStreamResource

/**
 * Created by alexei.vylegzhanin@gmail.com on 9/22/2018.
 */
@Tag("img-pan-zoom")
@HtmlImport("bower_components/img-pan-zoom/img-pan-zoom.html")
class ImgPanZoom() : HtmlContainer() {
    var src: String
        get() = get(srcDescriptor)
        set(src) = set(srcDescriptor, src)

    fun setSrc(src: AbstractStreamResource) {
        element.setAttribute("src", src)
    }
    companion object {
        private val srcDescriptor =
            PropertyDescriptors.attributeWithDefault("src", "")
    }
}
