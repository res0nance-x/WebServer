package r3.http

import org.json.JSONObject
import r3.collection.HashSimpleMap
import r3.util.humanReadableSize
import java.text.SimpleDateFormat
import java.util.*

data class DirEntry(
	val name: String,
	val href: String,
	val isDirectory: Boolean,
	val size: Long,
	val modifiedMillis: Long,
	val escName: String
)

// lazy, class-relative resource lookup, explicit UTF-8
private val templateEngine by lazy {
	val res = DirectoryListingRouter::class.java.getResource("DirListingTemplate.html")
		?: throw RuntimeException("Template resource DirListingTemplate.html not found on classpath")
	val templateText = res.readText(Charsets.UTF_8)
	SimpleTemplateEngine(HashSimpleMap<String, String>().apply {
		this["DirListingTemplate.html"] = templateText
	})
}

/**
 * Render a directory listing HTML. The function produces a self-contained HTML page with
 * a filter input (supports globbing like *.jpg), and client-side sorting by name/date/size.
 *
 * - uri: the request URI (for display)
 * - baseUri: uri directory base ending with '/'
 * - entries: list of DirEntry
 * - rfc5322: used to format the Last-Modified header display if needed
 */
fun renderDirectoryListing(uri: String, baseUri: String, entries: List<DirEntry>, rfc5322: SimpleDateFormat): String {
	val sb = StringBuilder()
	for (e in entries) {
		val displaySize = if (e.isDirectory) "-" else e.size.humanReadableSize()
		val mod = rfc5322.format(Date.from(java.time.Instant.ofEpochMilli(e.modifiedMillis)))
		val isDirFlag = if (e.isDirectory) "d" else "f"

		sb.append(
			"""
	<tr data-name="${escapeHtmlAttr(e.name)}" data-size="${e.size}" data-mod="${e.modifiedMillis}"
	data-isdir="$isDirFlag" data-href="${
				escapeHtmlAttr(
					e.href
				)
			}">
	<td><a href="${escapeHtmlAttr(e.href)}" onclick="openLink(event,this)">${e.escName}</a></td>
	<td>$displaySize</td>
	<td class="muted">$mod</td>
	</tr>
	"""
		)
	}
	val page = templateEngine.render("DirListingTemplate.html", JSONObject().apply {
		// escape the uri for safe HTML insertion
		put("uri", escapeHtmlAttr(uri))
		// baseUri is inserted into JS inside quotes in the template; escape it for JS string literal safety
		put("baseUri", escapeJsString(baseUri))
		put("rows", sb.toString())
	})

	return page
}

private fun escapeHtmlAttr(s: String): String {
	return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
}

private fun escapeJsString(s: String): String {
	return s.replace("\\", "\\\\")
		.replace("\"", "\\\"")
		.replace("\n", "\\n")
		.replace("\r", "\\r")
		.replace("\t", "\\t")
		// avoid ending the surrounding script tag
		.replace("</", "<\\/")
}