package r3.graffiti

import r3.content.ContentMeta
import r3.io.Writable
import r3.io.toDataInputStream
import r3.key.Key256
import r3.pke.*
import r3.source.BlockWritable
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger

class EncryptedContentMetaData(
	val eMeta: ByteArray,
	val author: PeerKey, // during creation this is an Identity. For storage and reading, this is a Peer
	val recipient: IdentityKey, // during creation this is the Peer. For storage and reading, this is an Identity
	val my: BigInteger,
	val ePass: ByteArray, // Encrypted password. Need recipient Identity to decrypt
	val contentKey: ContentKey,
	val sign: Signature
) : Writable {
	val key = EncryptedMetaKey(
		Key256(author.arr) xor Key256(recipient.arr) xor Key256(contentKey.arr)
	)

	fun decrypt(identity: Identity): Pair<ContentMeta, Password256> {
		if (identity.key != recipient) {
			error("The recipient key doesn't match the supplied identity key")
		}
		identity.createCipherKey(my).createDecrypt().doFinal(ePass).toDataInputStream().use { Password256.read(it) }
			.let { pass ->
				val metaArr = Encrypt.decrypt(Password256(pass), eMeta)
				return Pair(ContentMeta.read(metaArr.toDataInputStream()), pass)
			}
	}

	fun verify(peer: Peer): Boolean {
		val baos = ByteArrayOutputStream()
		baos.write(author.arr)
		baos.write(recipient.arr)
		baos.write(contentKey.arr)
		baos.write(eMeta)
		return peer.verify(baos.toByteArray(), sign)
	}

	override fun write(dos: DataOutputStream) {
		author.write(dos)
		recipient.write(dos)
		BigIntegerWritable(my).write(dos)
		BlockWritable(ePass).write(dos)
		contentKey.write(dos)
		sign.write(dos)
		dos.write(eMeta)
	}

	override fun toString(): String {
		return """author: $author, recipient: $recipient, contentKey: $contentKey"""
	}

	companion object {
		fun read(dis: DataInputStream): EncryptedContentMetaData {
			val author = PeerKey.read(dis)
			val recipient = IdentityKey.read(dis)
			val my = BigIntegerWritable.read(dis).bi
			val ePass = BlockWritable.read(dis).arr
			val contentKey = ContentKey.read(dis)
			val sign = Signature.read(dis)
			val eMeta = ByteArray(dis.available())
			dis.readFully(eMeta)
			return EncryptedContentMetaData(eMeta, author, recipient, my, ePass, contentKey, sign)
		}
	}
}