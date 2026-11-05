package app.cardfilter.domain.model

import kotlin.jvm.JvmInline

@JvmInline
value class CardId(val value: Long)

/** One user-defined property of a card: a name and a single text value. */
data class Attribute(
    val name: String,
    val value: String,
)

data class Card(
    val id: CardId,
    val topicId: TopicId,
    val title: String,
    val attributes: List<Attribute> = emptyList(),
    /** Whether an image is stored for this card; the bytes are fetched on demand, not carried here. */
    val hasImage: Boolean = false,
)
