package app.cardfilter.domain.search

import java.text.Normalizer

internal actual fun decompose(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
