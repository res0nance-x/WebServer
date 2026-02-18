package r3.net.tcp

import r3.io.log
import r3.net.discover.PeerAddressInfo
import r3.net.getAddressListInternal
import r3.pke.RelayKey
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class TCPServer(
	val nodeList: MutableList<TCPNode> = mutableListOf(),
	val tempDir: File,
	val contentHandler: (TCPNode, ByteArray, File?) -> Unit,
	val address: InetSocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 0)
) : Closeable {
	val socketServer = ServerSocket(address.port, 10, address.address)
	val peerAddressInfo: PeerAddressInfo = if (address.address.isAnyLocalAddress) {
		val addressList = getAddressListInternal()
		PeerAddressInfo(addressList, socketServer.localPort)
	} else {
		PeerAddressInfo(listOf(address.address as InetAddress), socketServer.localPort)
	}
	val key: RelayKey = peerAddressInfo.key
	fun start(daemon: Boolean = true) {
		// socket connection listen loop
		thread(isDaemon = daemon, name = "TCPServer") {
			while (!Thread.interrupted() && !socketServer.isClosed) {
				try {
					val sock = socketServer.accept()
					log("TCPServer: Received connection from ${sock.remoteSocketAddress}")
					handle(sock)
				} catch (_: Exception) {
					log("TCPServer has stopped")
				}
			}
		}
	}

	private fun handle(sock: Socket) {
		nodeList.add(TCPNode(sock, tempDir, contentHandler))
	}

	override fun close() {
		socketServer.close()
		for (node in nodeList) {
			try {
				node.close()
			} catch (_: Exception) {
			}
		}
	}
}