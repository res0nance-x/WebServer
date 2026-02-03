package r3.net.discover

import r3.io.log
import r3.io.toDataInputStream
import r3.net.createIP4DatagramSocket
import r3.net.getScanAddressIterator
import r3.net.usableNetworkInterfaceList
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.concurrent.thread

class ScanDiscover() {
	private val sockList = ArrayList<DatagramSocket>()
	private val scanPort = 52852
	fun discover(found: (info: PeerAddressInfo) -> Unit) {
		for (inf in usableNetworkInterfaceList()) {
			if (inf.supportsMulticast() && !inf.isLoopback && inf.isUp && !inf.isVirtual) {
				thread {
					val datagram = DatagramPacket(ByteArray(1500), 1500)
					val sock = inf.createIP4DatagramSocket()
					sockList.add(sock)
					thread {
						try {
							while (!Thread.interrupted()) {
								sock.receive(datagram)
								try {
									val info = datagram.data.toDataInputStream().use { dis -> PeerAddressInfo.read(dis) }
									found(info)
								} catch (e: Exception) {
									log("ScanDiscoverIP4: Error reading packet data")
								}
							}
						} catch (_: Exception) {
						}
					}
					thread {
						val scanAddressIterator = inf.getScanAddressIterator()
						var count = 0
						val time = System.currentTimeMillis()
						val packet = DatagramPacket(ByteArray(0), 0)
						packet.port = scanPort
						while (scanAddressIterator.hasNext() && count < 8192) {
							++count
							try {
								packet.address = scanAddressIterator.next()
								sock.send(packet)
								Thread.sleep(1)
							} catch (e: Exception) {
								log("ScanDiscoverIP4: $inf $e")
							}
						}
						log("ScanDiscoverIP4: Scanning took ${(System.currentTimeMillis() - time)} for $inf")
						Thread.sleep(500)
						sock.close()
					}
				}
			}
		}
	}
}