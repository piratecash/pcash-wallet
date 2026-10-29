package cash.p.terminal.modules.markdown

import cash.p.terminal.core.tryOrNull
import com.halilibo.richtext.markdown.node.AstImage
import com.halilibo.richtext.markdown.node.AstLink
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.markdown.node.AstNodeLinks
import com.halilibo.richtext.markdown.node.AstNodeType
import java.net.URL

/** Copies the tree with image and link destinations resolved against [baseUrl]; the AST itself is immutable. */
internal fun AstNode.withUrlsResolvedAgainst(baseUrl: String): AstNode {
    val base = tryOrNull { URL(baseUrl) } ?: return this
    return copyResolving(base, parent = null, previous = null)
}

private fun AstNode.copyResolving(base: URL, parent: AstNode?, previous: AstNode?): AstNode {
    val copy = AstNode(type.resolving(base), AstNodeLinks(parent = parent, previous = previous))
    var child = links.firstChild
    var lastCopied: AstNode? = null
    while (child != null) {
        val childCopy = child.copyResolving(base, parent = copy, previous = lastCopied)
        if (lastCopied == null) copy.links.firstChild = childCopy else lastCopied.links.next = childCopy
        lastCopied = childCopy
        child = child.links.next
    }
    copy.links.lastChild = lastCopied
    return copy
}

private fun AstNodeType.resolving(base: URL): AstNodeType = when (this) {
    is AstImage -> copy(destination = base.resolve(destination))
    is AstLink -> copy(destination = base.resolve(destination))
    else -> this
}

private fun URL.resolve(destination: String): String =
    tryOrNull { URL(this, destination).toString() } ?: destination
