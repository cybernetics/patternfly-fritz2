package org.patternfly

import kotlinx.browser.document

public object Id {

    private const val UNIQUE_ID = "id-"
    private var counter: Int = 0

    /** Creates an identifier guaranteed to be unique within this document. */
    public fun unique(): String {
        var id: String
        do {
            id = "$UNIQUE_ID$counter"
            counter = if (counter == Int.MAX_VALUE) {
                0
            } else {
                counter + 1
            }
        } while (document.getElementById(id) != null)
        return id
    }

    /** Creates an identifier guaranteed to be unique within this document. The unique part comes last. */
    public fun unique(prefix: String, vararg additionalIds: String): String = "${build(prefix, *additionalIds)}-${unique()}"

    public fun build(id: String, vararg additionalIds: String): String {
        val segments = listOf(id, *additionalIds)
        return segments.joinToString("-") { asId(it) }
    }

    public fun asId(text: String): String {
        return text.split("[-\\s]").asSequence()
            .map { it.replace("\\s+".toRegex(), "") }
            .map { it.replace("[^a-zA-Z0-9-_]".toRegex(), "") }
            .map { it.replace('_', '-') }
            .map { it.toLowerCase() }
            .filter { it.isNotEmpty() }
            .joinToString()
    }
}
