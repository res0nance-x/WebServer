package r3.net.tcp

import r3.io.log
import r3.net.discover.PeerAddressInfo
import r3.net.getAddressListInternal
import r3.pke.RelayKey
import r3.thread.pthread
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class TCPServer(
	val tempDir: File,
	val contentHandler: (TCPNode, ByteArray, File?) -> Unit,
	val address: InetSocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 0)
) : Closeable {
	val socketServer = ServerSocket(address.port, 10, address.address)
	val addressList = getAddressListInternal()
	val peerAddressInfo: PeerAddressInfo = PeerAddressInfo(addressList, socketServer.localPort)
	val key: RelayKey = peerAddressInfo.key
	val nodeList = ArrayList<TCPNode>()

	init {
		// socket connection listen loop
		pthread {
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