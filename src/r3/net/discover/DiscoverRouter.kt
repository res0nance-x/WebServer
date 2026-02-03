package r3.net.discover
//	val lock = ReentrantLock()
//	val condition = lock.newCondition()
//		lock.withLock {
//			condition.signalAll()
//		}
// ---------------------------------
//		lock.withLock {
//			condition.await()
//		}
//class DiscoverRouter(val base: String, val connectionManager: ConnectionManager) : IRouter {
//	override fun findRoute(session: IHTTPSession): Response? {
//		if (!session.uri.startsWith(base)) {
//			return null
//		}
//		val path = session.uri.substring(base.length)
//		return when (path) {
//			"list" -> {
//				val json = JSONObject()
//				for (me in connectionManager.nodeMap) {
//					json.put(me.key.toString(), me.value.toString())
//				}
//				json.toNanoHttpResponse()
//			}
//
//			else -> null
//		}
//	}
//}