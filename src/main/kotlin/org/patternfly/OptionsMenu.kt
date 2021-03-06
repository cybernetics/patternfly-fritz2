package org.patternfly

import dev.fritz2.binding.EmittingHandler
import dev.fritz2.binding.RootStore
import dev.fritz2.binding.SimpleHandler
import dev.fritz2.binding.mountSingle
import dev.fritz2.dom.Tag
import dev.fritz2.dom.html.Button
import dev.fritz2.dom.html.Div
import dev.fritz2.dom.html.Li
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.dom.html.Span
import dev.fritz2.lenses.IdProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import org.patternfly.dom.By
import org.patternfly.dom.Id
import org.patternfly.dom.aria
import org.patternfly.dom.debug
import org.patternfly.dom.matches
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.Node

// TODO Refactor and document me
// ------------------------------------------------------ dsl

/**
 * Creates a [OptionsMenu] component.
 *
 * @param store the store for the options menu
 * @param grouped whether the options menu contains groups or just flat items
 * @param multiSelect whether multiple entries can be selected
 * @param closeOnSelect whether to close the menu after selecting an item
 * @param align the alignment of the options menu
 * @param up controls the direction of the options menu
 * @param id the ID of the element
 * @param baseClass optional CSS class that should be applied to the element
 * @param content a lambda expression for setting up the component itself
 */
public fun <T> RenderContext.optionsMenu(
    store: OptionsMenuStore<T> = OptionsMenuStore(),
    grouped: Boolean = false,
    multiSelect: Boolean = false,
    closeOnSelect: Boolean = false,
    align: Align? = null,
    up: Boolean = false,
    id: String? = null,
    baseClass: String? = null,
    content: OptionsMenu<T>.() -> Unit = {}
): OptionsMenu<T> =
    register(
        OptionsMenu(store, grouped, multiSelect, closeOnSelect, align, up, id = id, baseClass = baseClass, job),
        content
    )

/**
 * Creates a text toggle. Specify the text using the [content] function.
 *
 * @param plain whether to use plain text in the toggle
 * @param baseClass optional CSS class that should be applied to the element
 * @param content a lambda for setting up the text toggle
 *
 * @sample org.patternfly.sample.OptionsMenuSample.textToggle
 * @sample org.patternfly.sample.OptionsMenuSample.plainTextToggle
 */
public fun <T> OptionsMenu<T>.textToggle(
    plain: Boolean = false,
    baseClass: String? = null,
    content: Span.() -> Unit
) {
    if (plain) {
        assignToggle(OptionsMenuPlainTextToggle(this, baseClass, job, content))
    } else {
        assignToggle(OptionsMenuTextToggle(this, baseClass, job, content))
    }
}

/**
 * Creates an icon toggle. Use the [content] function to setup the icon.
 *
 * @param baseClass optional CSS class that should be applied to the element
 * @param content a lambda for setting up the icon toggle
 *
 * @sample org.patternfly.sample.OptionsMenuSample.iconToggle
 */
public fun <T> OptionsMenu<T>.iconToggle(baseClass: String? = null, content: Button.() -> Unit) {
    assignToggle(OptionsMenuIconToggle(this, baseClass, job, content))
}

/**
 * Starts a block to add flat option menu items using the DSL.
 *
 * @sample org.patternfly.sample.OptionsMenuSample.items
 */
public fun <T> OptionsMenu<T>.items(block: ItemsBuilder<T>.() -> Unit = {}) {
    store.update(ItemsBuilder<T>().apply(block).build())
}

/**
 * Starts a block to add dropdown groups using the DSL.
 *
 * @sample org.patternfly.sample.OptionsMenuSample.groups
 */
public fun <T> OptionsMenu<T>.groups(block: GroupsBuilder<T>.() -> Unit = {}) {
    if (!grouped) {
        console.warn("Options menu ${domNode.debug()} has not been created using `grouped = true`")
    }
    store.update(GroupsBuilder<T>().apply(block).build())
}

// ------------------------------------------------------ tag

/**
 * PatternFly [options menu](https://www.patternfly.org/v4/components/options-menu/design-guidelines) component.
 *
 * An options menu is similar to a dropdown, but provides a way to select among a set of optional settings rather than trigger an action. A options menu consists of a toggle control to open and close a menu of [entries][Entry].
 *
 * You can choose between different toggle variations:
 * - [text toggle][OptionsMenuTextToggle]
 * - [plain text toggle][OptionsMenuPlainTextToggle]
 * - [icon toggle][DropdownIconToggle]
 *
 * The data in the menu is wrapped inside instances of [Entry] and managed by a [OptionsMenuStore]. Each [Entry] is either an [Item], a [Group] or a [Separator]. An [Item] can have additional properties such as an icon, a description or a disabled state.
 *
 * **Adding entries**
 *
 * Entries can be added by using the [OptionsMenuStore] or by using the DSL. Items can be grouped. Nested groups are not supported. See the samples below.
 *
 * **Rendering entries**
 *
 * By default the options menu uses a builtin function to render the [Item]s in the [OptionsMenuStore]. It uses the function passed to [selector] to select a string from [Item.item] which defaults to `{ it.toString() }`.
 *
 * If you don't want to use the builtin defaults you can specify a custom display function by calling [display]. In this case you have full control over the rendering of the data in the option menu entries.
 *
 * @sample org.patternfly.sample.OptionsMenuSample.dropdownDsl
 * @sample org.patternfly.sample.OptionsMenuSample.dropdownStore
 */
public class OptionsMenu<T> internal constructor(
    public val store: OptionsMenuStore<T>,
    internal val grouped: Boolean,
    multiSelect: Boolean,
    private val closeOnSelect: Boolean,
    optionsMenuAlign: Align?,
    up: Boolean,
    id: String?,
    baseClass: String?,
    job: Job
) : PatternFlyComponent<HTMLDivElement>, Div(
    id = id,
    baseClass = classes {
        +ComponentType.OptionsMenu
        +optionsMenuAlign?.modifier
        +("top".modifier() `when` up)
        +baseClass
    },
    job
) {

    private var selector: (T) -> String = { it.toString() }
    private var customDisplay: ComponentDisplay<Button, T>? = null
    private var defaultDisplay: ComponentDisplay<Button, Item<T>> = { item ->
        +this@OptionsMenu.selector(item.item)
    }

    private var toggle: Toggle<T, Node> = RecordingToggle()
    internal val toggleId: String = Id.unique(ComponentType.OptionsMenu.id, "tgl")

    /**
     * Manages the **c**ollapse / **e**xpand **s**tate of the [OptionsMenu]. Use this property if you want to track the collapse / expand state.
     *
     * @sample org.patternfly.sample.OptionsMenuSample.ces
     */
    public val ces: CollapseExpandStore = CollapseExpandStore { target ->
        !domNode.contains(target) && !target.matches(By.classname("options-menu".component("menu-item")))
    }

    init {
        store.multiSelect = multiSelect
        markAs(ComponentType.OptionsMenu)
        classMap(ces.data.map { expanded -> mapOf("expanded".modifier() to expanded) })

        val classes = classes {
            +"options-menu".component("menu")
            +optionsMenuAlign?.modifier
        }
        val tag = if (grouped) {
            div(baseClass = classes) {
                attr("hidden", true)
            }
        } else {
            ul(baseClass = classes) {
                attr("hidden", true)
            }
        }
        with(tag) {
            attr("role", "menu")
            attr("hidden", this@OptionsMenu.ces.data.map { !it })
            aria["labelledby"] = this@OptionsMenu.toggleId

            this@OptionsMenu.store.data.renderEach { entry ->
                when (entry) {
                    is Item<T> -> {
                        li(content = this@OptionsMenu.itemContent(entry))
                    }
                    is Group<T> -> {
                        section(baseClass = "options-menu".component("group")) {
                            entry.text?.let {
                                h1(baseClass = "options-menu".component("group", "title")) { +it }
                            }
                            ul {
                                entry.items.forEach { groupEntry ->
                                    when (groupEntry) {
                                        is Item<T> -> {
                                            li(content = this@OptionsMenu.itemContent(groupEntry))
                                        }
                                        is Separator<T> -> {
                                            divider(DividerVariant.LI)
                                        }
                                        else -> {
                                            console.warn("Nested groups are not supported for ${domNode.debug()}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is Separator<T> -> {
                        if (domNode.tagName.toLowerCase() == "ul") {
                            divider(DividerVariant.LI)
                        } else {
                            divider(DividerVariant.DIV)
                        }
                    }
                }
            }
        }
    }

    private fun itemContent(item: Item<T>): Li.() -> Unit = {
        attr("role", "menuitem")
        button(
            baseClass = classes {
                +"options-menu".component("menu-item")
                +("disabled".modifier() `when` item.disabled)
            }
        ) {
            attr("tabindex", "-1")
            if (item.disabled) {
                aria["disabled"] = true
                attr("disabled", "true")
            }
            if (this@OptionsMenu.customDisplay != null) {
                this@OptionsMenu.customDisplay?.invoke(this, item.item)
            } else {
                this@OptionsMenu.defaultDisplay.invoke(this, item)
            }
            if (item.selected) {
                span(baseClass = "options-menu".component("menu-item", "icon")) {
                    icon("check".fas())
                }
            }
            if (this@OptionsMenu.closeOnSelect) {
                clicks handledBy this@OptionsMenu.ces.collapse
            }
            clicks.map { item } handledBy this@OptionsMenu.store.selectHandler
        }
    }

    internal fun <N : Node> assignToggle(toggle: Toggle<T, N>) {
        // when switching from the recording to a valid toggle
        // replay the recorded values (if any)
        if (this.toggle is RecordingToggle<T> && toggle !is RecordingToggle<T>) {
            domNode.prepend(toggle.domNode)
            (this.toggle as RecordingToggle<T>).playback(toggle)
            this.toggle = toggle
        } else {
            console.warn("Reassignment of options menu toggle in ${domNode.debug()} not supported. Toggle has already been assigned to ${this.toggle::class.simpleName}.")
        }
    }

    /**
     * Sets the selector which is used by the built in display function to select a string from `T`.
     */
    public fun selector(selector: (T) -> String) {
        this.selector = selector
    }

    /**
     * Sets a custom display function to render the data inside the options menu.
     */
    public fun display(display: ComponentDisplay<Button, T>) {
        this.customDisplay = display
    }

    /**
     * Disables or enables the options menu toggle.
     */
    public fun disabled(value: Boolean) {
        toggle.disabled(value)
    }

    /**
     * Disables or enables the options menu toggle based on the values from the flow.
     */
    public fun disabled(value: Flow<Boolean>) {
        toggle.disabled(value)
    }
}

// ------------------------------------------------------ toggle

private fun <T> initToggle(optionsMenu: OptionsMenu<T>, tag: Tag<HTMLElement>) {
    with(tag) {
        domNode.id = optionsMenu.toggleId
        aria["haspopup"] = "listbox"
        aria["expanded"] = optionsMenu.ces.data.map { it.toString() }
        clicks handledBy optionsMenu.ces.toggle
    }
}

internal class OptionsMenuTextToggle<T>(
    optionsMenu: OptionsMenu<T>,
    baseClass: String?,
    job: Job,
    content: Span.() -> Unit
) : Toggle<T, HTMLButtonElement>,
    Button(baseClass = classes("options-menu".component("toggle"), baseClass), job = job) {

    init {
        initToggle(optionsMenu, this)
        span(baseClass = "options-menu".component("toggle", "text")) {
            content(this)
        }
        span(baseClass = "options-menu".component("toggle", "icon")) {
            icon("caret-down".fas())
        }
    }

    override fun disabled(value: Boolean) {
        disabled(value, trueValue = "")
    }

    override fun disabled(value: Flow<Boolean>) {
        disabled(value, trueValue = "")
    }
}

internal class OptionsMenuPlainTextToggle<T>(
    optionsMenu: OptionsMenu<T>,
    baseClass: String?,
    job: Job,
    content: Span.() -> Unit
) : Toggle<T, HTMLDivElement>,
    Div(
        baseClass = classes {
            +"options-menu".component("toggle")
            +"text".modifier()
            +"plain".modifier()
            +baseClass
        },
        job = job
    ) {

    private val toggleButton: Button

    init {
        span(baseClass = "options-menu".component("toggle", "text")) {
            content(this)
        }
        toggleButton = button(baseClass = "options-menu".component("toggle", "button")) {
            span(baseClass = "options-menu".component("toggle", "button", "icon")) {
                icon("caret-down".fas())
            }
        }
        initToggle(optionsMenu, toggleButton)
    }

    override fun disabled(value: Boolean) {
        domNode.classList.toggle("disabled".modifier(), value)
        toggleButton.disabled(value)
    }

    override fun disabled(value: Flow<Boolean>) {
        mountSingle(job, value) { v, _ -> disabled(v) }
    }
}

internal class OptionsMenuIconToggle<T>(
    optionsMenu: OptionsMenu<T>,
    baseClass: String?,
    job: Job,
    content: Button.() -> Unit
) : Toggle<T, HTMLButtonElement>,
    Button(
        baseClass = classes {
            +"options-menu".component("toggle")
            +"plain".modifier()
            +baseClass
        },
        job = job
    ) {

    init {
        initToggle(optionsMenu, this)
        content(this)
    }

    override fun disabled(value: Boolean) {
        disabled(value, trueValue = "")
    }

    override fun disabled(value: Flow<Boolean>) {
        disabled(value, trueValue = "")
    }
}

// ------------------------------------------------------ store
/**
 * Store containing the data shown in a options menu. The data is wrapped inside instances of [Entry]. An entry is either an [Item] or a [Group] of [Item]s. An [Item] can have additional properties such as an icon, a description or a disabled state.
 *
 * Most of the flows and handlers in this store use [Item] instead of the wrapped data. Use one of the `unwrap()` functions to get the actual payload.
 *
 * @sample org.patternfly.sample.OptionsMenuSample.unwrap
 */
public class OptionsMenuStore<T>(override val idProvider: IdProvider<T, String> = { Id.build(it.toString()) }) :
    WithIdProvider<T>,
    RootStore<List<Entry<T>>>(listOf()) {

    internal var multiSelect: Boolean = false

    internal val selectHandler: EmittingHandler<Item<T>, Item<T>> = handleAndEmit { items, item ->
        emit(item)
        items.map { currentEntry ->
            when (currentEntry) {
                is Item<T> -> select(item, currentEntry)
                is Group<T> -> {
                    if (currentEntry.id == item.group?.id) {
                        val groupItems = currentEntry.items.map { groupEntry ->
                            when (groupEntry) {
                                is Item<T> -> select(item, groupEntry)
                                else -> groupEntry
                            }
                        }
                        currentEntry.copy(items = groupItems)
                    } else {
                        currentEntry
                    }
                }
                else -> currentEntry
            }
        }
    }

    private fun select(item: Item<T>, current: Item<T>): Entry<T> =
        if (multiSelect) {
            if (sameId(item, current)) {
                current.copy(selected = !current.selected)
            } else {
                current
            }
        } else {
            if (sameId(item, current)) {
                if (current.selected) {
                    current
                } else {
                    current.copy(selected = true)
                }
            } else {
                current.copy(selected = false)
            }
        }

    private fun sameId(item: Item<T>, current: Item<T>) = itemId(item.item) == itemId(current.item)

    /**
     * Flow with the last selected items.
     */
    public val selects: Flow<Item<T>> = selectHandler

    /**
     * Flow containing all selected items.
     */
    public val selection: Flow<List<Item<T>>> = data.flatItems()
        .drop(1) // drop initial (empty) data
        .map { items ->
            items.filter { it.selected }
        }

    /**
     * Flow containing the first selected item.
     */
    public val singleSelection: Flow<Item<T>?> = selection.map { it.firstOrNull() }

    /**
     * Wraps the specified data inside instances of [Item] and adds them to the list of existing entries.
     */
    public val addAll: SimpleHandler<List<T>> = handle { items, newItems ->
        items + newItems.map {
            Item(it, disabled = false, selected = false, description = "", icon = null, group = null)
        }
    }
}
