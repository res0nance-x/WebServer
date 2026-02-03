package r3.net.discover

import r3.hash.hash256
import r3.io.Writable
import r3.net.tcp.InetAddressWritable
import r3.pke.RelayKey
import r3.pke.name
import r3.util.compare
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress

class PeerAddressInfo(val addrList: List<InetAddress>, val port: Int) : Writable {
	val key: RelayKey = run {
		val baos = ByteArrayOutputStream()
		val list = ArrayList<ByteArray>()
		for (addr in addrList) {
			list.add(addr.address)
		}
		list.sortWith { a, b -> compare(a, b) }
		for (addr in list) {
			baos.write(addr)
		}
		RelayKey(baos.toByteArray().hash256())
	}

	override fun write(dos: DataOutputStream) {
		dos.writeInt(addrList.size)
		for (addr in addrList) {
			InetAddressWritable(addr).write(dos)
		}
		dos.writeInt(port)
	}

	companion object {
		fun read(dis: DataInputStream): PeerAddressInfo {
			return PeerAddressInfo(
				List(dis.readInt()) { InetAddressWritable.read(dis).addr },
				dis.readInt()
			)
		}
	}

	override fun toString(): String {
		return "${key.name} $key ${addrList.joinToString(", ", "[", "]")}:$port"
	}
}