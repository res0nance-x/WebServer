package r3.content

import r3.source.Source

interface Content : Source {
	val name: String
	val type: String
	val created: Long // epoch milliseconds
}

val Content.meta: ContentMeta
	get() {
		return ContentMeta(this)
	}