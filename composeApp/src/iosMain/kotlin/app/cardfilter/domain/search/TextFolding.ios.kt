package app.cardfilter.domain.search

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun decompose(text: String): String =
    (text as NSString).decomposedStringWithCanonicalMapping
