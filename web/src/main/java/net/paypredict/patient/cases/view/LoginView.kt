package net.paypredict.patient.cases.view

import com.vaadin.flow.component.*
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.PasswordField
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.router.BeforeEnterEvent
import com.vaadin.flow.router.BeforeEnterObserver
import com.vaadin.flow.router.Route
import net.paypredict.patient.cases.data.PPUser
import net.paypredict.patient.cases.data.checkPassword

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/14/2018.
 */
@Route("login")
class LoginView : Composite<HorizontalLayout>(), BeforeEnterObserver {
    override fun beforeEnter(event: BeforeEnterEvent) {
        val ppUser = event.ui.ppUser
        val ppRouteOnAuth = event.ui.ppRouteOnAuth
        if (ppUser != null && ppRouteOnAuth != null) {
            event.ui.navigate(ppRouteOnAuth)
            event.rerouteTo(ppRouteOnAuth)
        }
    }

    init {
        content.defaultVerticalComponentAlignment = FlexComponent.Alignment.CENTER
        content.setSizeFull()
        content += VerticalLayout().apply {
            defaultHorizontalComponentAlignment = FlexComponent.Alignment.CENTER
            this += VerticalLayout().apply {
                setSizeUndefined()

                val userName = TextField("User")
                val password = PasswordField("Password")
                val login = Button("Login").apply {
                    element.setAttribute("theme", "primary")
                    addClickListener { _ ->
                        ui.ifPresent { ui ->
                            val ppUser = PPUser(userName.value)
                            if (ppUser.checkPassword(password.value)) {
                                ui.ppUser = ppUser
                                ui.navigate(ui.ppRouteOnAuth)
                            } else {
                                Dialog(H3("Invalid User Name or Password")).open()
                            }
                        }
                    }
                }

                val clickLogin = ComponentEventListener<KeyPressEvent> {
                    login.click()
                }
                userName.addKeyPressListener(Key.ENTER, clickLogin)
                password.addKeyPressListener(Key.ENTER, clickLogin)

                this += userName
                this += password
                this += login
                setHorizontalComponentAlignment(FlexComponent.Alignment.END, login)

                userName.focus()
            }
        }
    }

}

inline fun <reified T : Component> checkUserOnBeforeEnterEvent(event: BeforeEnterEvent) {
    if (event.ui.ppUser == null) {
        event.ui.ppRouteOnAuth = T::class.java
        event.rerouteTo(LoginView::class.java)
    }
}

@Suppress("UNCHECKED_CAST")
var UI.ppRouteOnAuth: Class<out Component>?
    get(): Class<out Component>? =
        session.getAttribute("ppRouteOnAuth") as? Class<Component>
    set(value) {
        session.setAttribute("ppRouteOnAuth", value)
    }

var UI.ppUser
    get(): PPUser? =
        session.getAttribute("ppUser") as? PPUser
    set(value) {
        session.setAttribute("ppUser", value)
    }
