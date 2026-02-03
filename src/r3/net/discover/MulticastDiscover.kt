package r3.net.discover

import r3.io.log
import r3.io.toDataInputStream
import r3.net.createIP6DatagramSocket
import r3.net.usableNetworkInterfaceList
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread

class MulticastDiscover {
	val ip6FixedMulticastAddress =
		InetSocketAddress(InetAddress.getByName("ff32:8395:4ab6:e403:8f2a:b92f:beaa:80da"), 45228)
	val sockList = mutableListOf<DatagramSocket>()
	fun discover(found: (PeerAddressInfo) -> Unit) {
		for (inf in usableNetworkInterfaceList()) {
			if (inf.supportsMulticast() && !inf.isLoopback && inf.isUp && !inf.isVirtual) {
				thread {
					try {
						val datagram = DatagramPacket(ByteArray(1500), 1500)
						val sock = inf.createIP6DatagramSocket()
						sockList.add(sock)
						sock.send(DatagramPacket(ByteArray(0), 0, ip6FixedMulticastAddress))
						sock.receive(datagram)
						found(PeerAddressInfo.read(datagram.data.toDataInputStream()))
					} catch (e: Exception) {
						log("MulticastDiscover: Couldn't create DatagramSocket on $inf with error $e")
					}
				}
			}
		}
		Thread.sleep(1000)
		for (sock in sockList) {
			try {
				sock.close()
			} catch (_: Exception) {
			}
		}
	}
}