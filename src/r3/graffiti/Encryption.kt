package r3.graffiti

import r3.content.ContentMeta
import r3.io.serialize
import r3.pke.*
import java.io.ByteArrayOutputStream

fun sign(from: Identity, to: Peer, contentKey: ContentKey, eMeta: ByteArray): Signature {
	val baos = ByteArrayOutputStream()
	baos.write(from.key.arr)
	baos.write(to.key.arr)
	baos.write(contentKey.arr)
	baos.write(eMeta)
	return from.sign(baos.toByteArray())
}

fun ContentMeta.encrypt(
	author: Identity,
	recipient: Peer,
	encryptedFileHash: ContentKey,
	pass: Password256
): EncryptedContentMetaData {
	val eMeta = Encrypt.encrypt(pass, this.serialize())
	val (my, cipher) = recipient.createShared()
	val ePass = cipher.createEncrypt().doFinal(pass.arr)
	val sign = author.sign(recipient, encryptedFileHash, eMeta)
	return EncryptedContentMetaData(
		eMeta,
		PeerKey(author.key.arr), // Sender is a Peer from the receiver's perspective
		IdentityKey(recipient.key.arr), // receiver is Identity (only identity can decrypt)
		my,
		ePass,
		encryptedFileHash,
		sign
	)
}