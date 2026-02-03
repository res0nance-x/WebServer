package r3.pack
//class ZipPack(file: File) : Pack {
//	private val map = HashSimpleMap<Key256, Content>()
//
//	init {
//		val zip = ZipFile(file)
//		for (x in zip.entries()) {
//			val c = ZipEntryContent(zip, x)
//			map[c.key] = c
//		}
//	}
//
//	override val size: Int
//		get() = map.size
//	override val keys: Set<Key256>
//		get() = HashSet(map.keys)
//
//	override fun get(key: Key256): Content? {
//		return map[key]
//	}
//
//	override fun visit(visitor: (Key256, Content) -> Unit) {
//		map.visit(visitor)
//	}
//}