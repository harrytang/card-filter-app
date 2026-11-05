package app.cardfilter.domain.model

import kotlin.jvm.JvmInline

@JvmInline
value class QuickFilterId(val value: Long)

/**
 * A saved pair of search queries for one topic: [include] is the normal search and [exclude] the
 * "not" search. Applying it to the card list means putting both texts into the two search fields.
 */
data class QuickFilter(
    val id: QuickFilterId,
    val topicId: TopicId,
    val name: String,
    val include: String,
    val exclude: String,
)
