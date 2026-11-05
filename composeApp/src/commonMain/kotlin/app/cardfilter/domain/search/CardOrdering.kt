package app.cardfilter.domain.search

import app.cardfilter.domain.model.Card

/** Favorites first; the order inside each group is kept (the sort is stable). */
fun List<Card>.favoritesFirst(): List<Card> = sortedByDescending { it.isFavorite }
