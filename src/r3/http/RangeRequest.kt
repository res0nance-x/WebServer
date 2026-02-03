package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.NanoHTTPD.MIME_PLAINTEXT
import org.nanohttpd.protocols.http.request.Method
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse
import org.nanohttpd.protocols.http.response.Status
import r3.content.Content
import r3.io.BoundedInputStream
import r3.io.log
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class Range(var start: Long, var end: Long)

fun parseRange(header: Map<String, String>): Range {
    var startFrom = 0L
    var endAt = -1L
    var range = header["range"]
    if (range != null) {
        if (range.startsWith("bytes=")) {
            range = range.substring("bytes=".length)
            // Support multiple ranges by using only the first range spec (e.g. "0-99,200-299").
            val first = range.split(',')[0].trim()
            val minus = first.indexOf('-')
            try {
                if (minus >= 0) {
                    val left = first.take(minus)
                    val right = first.substring(minus + 1)
                    if (left.isNotEmpty()) {
                        // "start-" or "start-end"
                        startFrom = left.toLong()
                    }
                    if (right.isNotEmpty()) {
                        // "-suffix" or "start-end"
                        endAt = right.toLong()
                    }
                    if (left.isEmpty() && right.isNotEmpty()) {
                        // suffix form: we encode as negative start to indicate suffix length
                        startFrom = -endAt
                        endAt = -1L
                    }
                }
            } catch (_: NumberFormatException) {
                log("RangeRequest: failure to parse range header $range")
            }
        }
    }
    return Range(startFrom, endAt)
}

private fun skipFully(stream: java.io.InputStream, toSkip: Long) {
    var remaining = toSkip
    val buf = ByteArray(8192)
    while (remaining > 0) {
        val skipped = try {
            val s = stream.skip(remaining)
            if (s > 0) s else 0L
        } catch (_: Exception) {
            0L
        }
        if (skipped > 0) {
            remaining -= skipped
            continue
        }
        // if skip couldn't progress, read and discard up to remaining or buffer
        val r = stream.read(buf, 0, if (remaining < buf.size) remaining.toInt() else buf.size)
        if (r <= 0) break
        remaining -= r
    }
}

fun rangeRequestResponse(session: IHTTPSession, content: Content): Response {
    val startNs = System.nanoTime()
    val requestHeader: Map<String, String> = session.headers
    // Unified ETag: use lastModified (content.created) and length to form a stable ETag
    val eTag = "${content.created}-${content.length}"
    val rangeHeader = (requestHeader["range"] != null)
    val ifRange = requestHeader["if-range"]
    val isGetOrHead = (session.method == Method.GET || session.method == Method.HEAD)
    // helper to strip surrounding quotes and weak prefix
    fun stripETag(s: String): String = s.trim().let { v ->
        var x = v
        if (x.startsWith("W/")) x = x.substring(2)
        x.trim().trim('"')
    }

    // Determine If-Range match: it can be an ETag (possibly quoted or weak) or an HTTP-date
    val headerIfRangeMissingOrMatching: Boolean = if (ifRange == null) {
        true
    } else {
        val fr = ifRange.trim()
        if (fr.startsWith("\"") || fr.startsWith("W/")) {
            stripETag(fr) == eTag
        } else {
            // try parse as RFC1123 date and compare to content.created
            try {
                val dt = ZonedDateTime.parse(fr, DateTimeFormatter.RFC_1123_DATE_TIME)
                val tms = dt.toInstant().toEpochMilli()
                // The date indicates the client's copy time; if the resource has NOT been modified since that date
                // then the server may honor the Range. That is true when serverLastModified <= date (tms >= content.created).
                tms >= content.created
            } catch (_: DateTimeParseException) {
                false
            }
        }
    }

    val ifNoneMatch = requestHeader["if-none-match"]
    // support multiple ETags in If-None-Match (comma separated) and quoted/weak forms
    val headerIfNoneMatchPresentAndMatching: Boolean = ifNoneMatch != null && (
        ifNoneMatch.trim() == "*" ||
            ifNoneMatch.split(",").map { stripETag(it) }.contains(eTag)
    )
    val range = parseRange(requestHeader)
    val mimeType = MimeMap[content.type] ?: "application/octet-stream"

    // helper to format Last-Modified header from content.created
    val lastModifiedHeader: String = try {
        DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(content.created))
    } catch (_: Exception) {
        ""
    }

    // Conditional GET/HEAD: If-None-Match has priority over If-Modified-Since
    if (isGetOrHead && headerIfNoneMatchPresentAndMatching) {
        return newFixedLengthResponse(Status.NOT_MODIFIED, mimeType, "").apply {
            addHeader("Accept-Ranges", "bytes")
            addHeader("ETag", '"' + eTag + '"')
            if (lastModifiedHeader.isNotEmpty()) addHeader("Last-Modified", lastModifiedHeader)
        }
    }

    // Support If-Modified-Since as a conditional GET when If-None-Match is not present
    val ifModifiedSince = requestHeader["if-modified-since"]
    if (isGetOrHead && ifNoneMatch == null && ifModifiedSince != null) {
        try {
            val dt = ZonedDateTime.parse(ifModifiedSince, DateTimeFormatter.RFC_1123_DATE_TIME)
            val tms = dt.toInstant().toEpochMilli()
            if (content.created <= tms) {
                // Not modified since the given date
                return newFixedLengthResponse(Status.NOT_MODIFIED, mimeType, "").apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("ETag", '"' + eTag + '"')
                    if (lastModifiedHeader.isNotEmpty()) addHeader("Last-Modified", lastModifiedHeader)
                }
            }
        } catch (_: Exception) {
            // ignore parse errors and continue
        }
    }

    // handle suffix ranges encoded as negative start
    var reqStart = range.start
    var reqEnd = range.end
    if (reqStart < 0) {
        // suffix length
        val suffix = -reqStart
        if (suffix <= 0) {
            reqStart = 0
        } else if (suffix >= content.length) {
            reqStart = 0
        } else {
            reqStart = content.length - suffix
        }
        reqEnd = -1L
    }

    // If end is specified and it's before start, that's an invalid range -> 416
    if (reqEnd in 0..<reqStart) {
        return newFixedLengthResponse(
            Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT,
            ""
        ).apply {
            addHeader("Accept-Ranges", "bytes")
            addHeader("Content-Range", "bytes */${content.length}")
            addHeader("ETag", '"' + eTag + '"')
        }
    }

    // Zero-length resources cannot satisfy any byte-range request
    if (rangeHeader && headerIfRangeMissingOrMatching && content.length == 0L) {
        return newFixedLengthResponse(
            Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT,
            ""
        ).apply {
            addHeader("Accept-Ranges", "bytes")
            addHeader("Content-Range", "bytes */0")
            addHeader("ETag", '"' + eTag + '"')
            if (lastModifiedHeader.isNotEmpty()) addHeader("Last-Modified", lastModifiedHeader)
            addHeader("X-Server-Time-ms", (((System.nanoTime() - startNs) / 1000000L).toString()))
        }
    }

    val response =
        if (headerIfRangeMissingOrMatching && rangeHeader && reqStart >= 0 && reqStart < content.length) {
            if (reqEnd < 0 || reqEnd >= content.length) {
                reqEnd = content.length - 1
            }
            var newLen = reqEnd - reqStart + 1
            if (newLen < 0) newLen = 0
            if (session.method == Method.GET) {
                val stream = content.createInputStream()
                skipFully(stream, reqStart)
                newFixedLengthResponse(
                    Status.PARTIAL_CONTENT, mimeType,
                    BufferedInputStream(BoundedInputStream(stream, newLen)), newLen
                ).apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("Content-Range", "bytes ${reqStart}-${reqEnd}/${content.length}")
                    addHeader("ETag", '"' + eTag + '"')
                    if (lastModifiedHeader.isNotEmpty()) addHeader("Last-Modified", lastModifiedHeader)
                    addHeader("X-Server-Time-ms", (((System.nanoTime() - startNs) / 1000000L).toString()))
                }
            } else {
                newFixedLengthResponse(
                    Status.PARTIAL_CONTENT, mimeType,
                    ByteArrayInputStream(ByteArray(0)), newLen
                ).apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("Content-Range", "bytes ${reqStart}-${reqEnd}/${content.length}")
                    addHeader("ETag", '"' + eTag + '"')
                    if (lastModifiedHeader.isNotEmpty()) addHeader("Last-Modified", lastModifiedHeader)
                    addHeader("X-Server-Time-ms", (((System.nanoTime() - startNs) / 1000000L).toString()))
                }
            }
        } else {
            if (headerIfRangeMissingOrMatching && rangeHeader && reqStart >= content.length) {
                newFixedLengthResponse(
                    Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT,
                    ""
                ).apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("Content-Range", "bytes */${content.length}")
                    addHeader("ETag", '"' + eTag + '"')
                    if (lastModifiedHeader.isNotEmpty()) addHeader("Last-Modified", lastModifiedHeader)
                    addHeader("X-Server-Time-ms", (((System.nanoTime() - startNs) / 1000000L).toString()))
                }
            } else {
                val istream = if (session.method == Method.GET) {
                    BufferedInputStream(content.createInputStream())
                } else {
                    ByteArrayInputStream(ByteArray(0))
                }
                newFixedLengthResponse(
                    Status.OK, mimeType,
                    istream, content.length
                ).apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("ETag", '"' + eTag + '"')
                    if (lastModifiedHeader.isNotEmpty()) addHeader("Last-Modified", lastModifiedHeader)
                    addHeader("X-Server-Time-ms", (((System.nanoTime() - startNs) / 1000000L).toString()))
                }
            }
        }
    return response
}
