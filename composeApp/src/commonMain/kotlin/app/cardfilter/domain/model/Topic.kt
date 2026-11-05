package app.cardfilter.domain.model

import kotlin.jvm.JvmInline

@JvmInline
value class TopicId(val value: Long)

data class Topic(
    val id: TopicId,
    val name: String,
)
