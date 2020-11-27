package org.patternfly

import dev.fritz2.binding.RootStore
import dev.fritz2.binding.SimpleHandler
import dev.fritz2.dom.html.A
import dev.fritz2.dom.html.Div
import dev.fritz2.dom.html.Img
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.dom.html.TextElement
import dev.fritz2.elemento.aria
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.patternfly.ButtonVariation.plain
import org.w3c.dom.Document
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement

// ------------------------------------------------------ dsl

/**
 * Creates the [Page] component.
 *
 * @param id the ID of the element
 * @param baseClass optional CSS class that should be applied to the element
 * @param content a lambda expression for setting up the component itself
 */
public fun RenderContext.page(
    id: String? = null,
    baseClass: String? = null,
    content: Page.() -> Unit = {}
): Page {
    val page = Page(id = id, baseClass = baseClass, job)
    Singletons.page = page
    return register(page, content)
}

/**
 * Creates the [Header] component inside the [Page].
 *
 * @param id the ID of the element
 * @param baseClass optional CSS class that should be applied to the element
 * @param content a lambda expression for setting up the component itself
 */
public fun Page.pageHeader(
    id: String? = null,
    baseClass: String? = null,
    content: Header.() -> Unit = {}
): Header {
    val header = Header(this, id = id, baseClass = baseClass, job)
    Singletons.header = header
    return register(header, content)
}

/**
 * Creates the [Brand] component inside the [Header].
 *
 * @param id the ID of the element
 * @param baseClass optional CSS class that should be applied to the element
 * @param content a lambda expression for setting up the component itself
 */
public fun Header.brand(
    id: String? = null,
    baseClass: String? = null,
    content: Brand.() -> Unit = {}
): Brand = register(Brand(this.page.sidebarStore, id = id, baseClass = baseClass, job), content)

/**
 * Creates a container for the tools inside the [Header].
 *
 * @param id the ID of the element
 * @param baseClass optional CSS class that should be applied to the element
 * @param content a lambda expression for setting up the component itself
 */
public fun Header.headerTools(
    id: String? = null,
    baseClass: String? = null,
    content: Div.() -> Unit = {}
): Div = register(Div(id = id, baseClass = classes("page".component("header", "tools"), baseClass), job), content)

/**
 * Creates the [Sidebar] component inside the [Page].
 *
 * @param id the ID of the element
 * @param baseClass optional CSS class that should be applied to the element
 * @param content a lambda expression for setting up the component itself
 */
public fun Page.pageSidebar(
    id: String? = null,
    baseClass: String? = null,
    content: Div.() -> Unit = {}
): Sidebar {
    val sidebar = register(Sidebar(this.sidebarStore, id = id, baseClass = baseClass, job, content), {})
    Singletons.sidebar = sidebar
    (MainScope() + job).launch {
        sidebarStore.visible(true)
    }
    return sidebar
}

/**
 * Creates the [PageMain] container inside the [Page].
 *
 * @param id the ID of the element
 * @param baseClass optional CSS class that should be applied to the element
 * @param content a lambda expression for setting up the component itself
 */
public fun Page.pageMain(
    id: String? = null,
    baseClass: String? = null,
    content: PageMain.() -> Unit = {}
): PageMain {
    val pageMain = PageMain(id = id, baseClass = baseClass, job)
    Singletons.pageMain = pageMain
    return register(pageMain, content)
}

/**
 * Creates a [PageSection] container.
 *
 * @param id the ID of the element
 * @param baseClass optional CSS class that should be applied to the element
 * @param content a lambda expression for setting up the component itself
 */
public fun RenderContext.pageSection(
    id: String? = null,
    baseClass: String? = null,
    content: PageSection.() -> Unit = {}
): PageSection = register(PageSection(id = id, baseClass = baseClass, job), content)

// ------------------------------------------------------ tag

/**
 * PatternFly [page](https://www.patternfly.org/v4/components/page/design-guidelines) component.
 *
 * A page component is used to create the basic structure of an application. It should be added directly to the
 * document body.
 *
 * A typical page setup with a header, brand, tools, sidebar and navigation might look like this:
 *
 * @sample PageSamples.typicalSetup
 */
public class Page internal constructor(id: String?, baseClass: String?, job: Job) :
    PatternFlyComponent<HTMLDivElement>, Div(id = id, baseClass = classes(ComponentType.Page, baseClass), job) {

    internal val sidebarStore: SidebarStore = SidebarStore()

    init {
        markAs(ComponentType.Page)
    }
}

/**
 * [PatternFly header](https://www.patternfly.org/v4/components/page/design-guidelines) component.
 */
public class Header internal constructor(internal val page: Page, id: String?, baseClass: String?, job: Job) :
    PatternFlyComponent<HTMLElement>,
    TextElement("header", id = id, baseClass = classes(ComponentType.PageHeader, baseClass), job) {

    init {
        markAs(ComponentType.PageHeader)
        attr("role", "banner")
    }
}

/**
 * [PatternFly brand](https://www.patternfly.org/v4/components/page/design-guidelines) component.
 */
public class Brand internal constructor(sidebarStore: SidebarStore, id: String?, baseClass: String?, job: Job) :
    Div(id = id, baseClass = classes("page".component("header", "brand"), baseClass), job) {

    private var link: A
    private lateinit var img: Img

    init {
        div(baseClass = "page".component("header", "brand", "toggle")) {
            attr("hidden", sidebarStore.data.map { !it.visible })
            classMap(sidebarStore.data.map { mapOf("display-none".util() to it.visible) })
            clickButton(plain) {
                aria["expanded"] = sidebarStore.data.map { it.expanded.toString() }
                icon("bars".fas())
            } handledBy sidebarStore.toggle
        }
        this@Brand.link = a(baseClass = "page".component("header", "brand", "link")) {
            href("#")
            this@Brand.img = img(baseClass = "brand".component()) {}
        }
    }

    /**
     * Sets the link to the homepage of the application.
     */
    public fun home(href: String) {
        link.href(href)
    }

    /**
     * Sets the image for the brand.
     */
    public fun img(src: String, content: Img.() -> Unit = {}) {
        img.apply(content).src(src)
    }
}

/**
 * [PatternFly sidebar](https://www.patternfly.org/v4/components/page/design-guidelines) component.
 *
 * If a sidebar is added to the page, a toggle button is displayed in the [Header] to toggle and expand the sidebar.
 * If you want to show and hide the sidebar manually (e.g. because some views don't require a sidebar), please use
 * [SidebarStore].
 */
public class Sidebar internal constructor(
    private val sidebarStore: SidebarStore,
    id: String?,
    baseClass: String?,
    job: Job,
    content: Div.() -> Unit
) : PatternFlyComponent<HTMLDivElement>, Div(id = id, baseClass = classes(ComponentType.PageSidebar, baseClass), job) {

    init {
        markAs(ComponentType.PageSidebar)
        attr("hidden", sidebarStore.data.map { !it.visible })
        classMap(sidebarStore.data.map {
            mapOf(
                "display-none".util() to it.visible,
                "collapsed".modifier() to !it.expanded,
                "expanded".modifier() to it.expanded
            )
        })
        div(baseClass = "page".component("sidebar", "body")) {
            content(this)
        }
    }

    public fun visible(value: Boolean) {
        sidebarStore.visible(value)
    }

    public fun visible(value: Flow<Boolean>) {
        value handledBy sidebarStore.visible
    }

}

/**
 * Main container of the [Page].
 */
public class PageMain internal constructor(id: String?, baseClass: String?, job: Job) :
    PatternFlyComponent<HTMLElement>,
    TextElement("main", id = id, baseClass = classes(ComponentType.Main, baseClass), job) {

    init {
        markAs(ComponentType.Main)
        attr("role", "main")
        attr("tabindex", "-1")
    }
}

/**
 * Page section container inside the [PageMain] container.
 */
public class PageSection internal constructor(id: String?, baseClass: String?, job: Job) :
    PatternFlyComponent<HTMLElement>,
    TextElement("section", id = id, baseClass = classes(ComponentType.Section, baseClass), job) {

    init {
        markAs(ComponentType.Section)
    }
}

// ------------------------------------------------------ store

internal data class SidebarStatus(val visible: Boolean, val expanded: Boolean)

internal class SidebarStore : RootStore<SidebarStatus>(SidebarStatus(visible = false, expanded = true)) {

    val visible: SimpleHandler<Boolean> = handle { status, visible ->
        status.copy(visible = visible)
    }

    val toggle: SimpleHandler<Unit> = handle { it.copy(expanded = !it.expanded) }
}

// ------------------------------------------------------ singleton

public fun Document.page(): Page? = Singletons.page
public fun Document.pageHeader(): Header? = Singletons.header
public fun Document.pageSidebar(): Sidebar? = Singletons.sidebar
public fun Document.pageMain(): PageMain? = Singletons.pageMain

internal object Singletons {
    var page: Page? = null
    var header: Header? = null
    var sidebar: Sidebar? = null
    var pageMain: PageMain? = null
}