package yomira.tv.companion

import fi.iki.elonen.NanoHTTPD
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.FileInputStream
import java.security.SecureRandom

/**
 * A tiny web server that lets a phone on the same network hand text or files to the TV.
 *
 * Typing a repository URL with a remote is slow, and many TV boxes (Fire TV included) have no
 * working system file picker. While a companion dialog is open the TV shows a QR code for this
 * server; the phone opens it in its browser and sends the text or uploads/downloads a backup.
 * Every request must carry the random token from the QR code, and the server stops as soon as the
 * dialog closes.
 */
class CompanionServer(
    private val mode: Mode,
    private val page: PageText,
    private val uploadDir: File? = null,
    private val onText: (String) -> Unit = {},
    private val onUpload: (File) -> Unit = {},
) : NanoHTTPD(0) {

    sealed interface Mode {
        data object Text : Mode
        data object Upload : Mode
        data class Download(val file: File, val downloadName: String) : Mode
    }

    /** Localized copy for the phone page. */
    data class PageText(
        val title: String,
        val description: String,
        val inputLabel: String,
        val submit: String,
        val sent: String,
        val download: String,
    )

    val token: String = ByteArray(TOKEN_BYTES)
        .also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    fun url(host: String): String = "http://$host:$listeningPort/?t=$token"

    override fun serve(session: IHTTPSession): Response {
        if (session.parameters["t"]?.firstOrNull() != token) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden")
        }
        return try {
            when {
                session.method == Method.GET && session.uri == "/" -> html(formPage())
                session.method == Method.POST && session.uri == "/text" && mode == Mode.Text -> receiveText(session)
                session.method == Method.POST && session.uri == "/upload" && mode == Mode.Upload -> receiveUpload(session)
                session.method == Method.GET && session.uri == "/download" && mode is Mode.Download -> sendFile(mode)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Companion request failed" }
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "Error")
        }
    }

    private fun receiveText(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val text = session.parameters["text"]?.firstOrNull()?.trim().orEmpty()
        if (text.isEmpty() || text.length > MAX_TEXT_LENGTH) {
            return html(formPage())
        }
        onText(text)
        return html(donePage())
    }

    private fun receiveUpload(session: IHTTPSession): Response {
        val length = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (length > MAX_UPLOAD_BYTES) {
            return newFixedLengthResponse(Response.Status.PAYLOAD_TOO_LARGE, MIME_PLAINTEXT, "File too large")
        }
        val files = HashMap<String, String>()
        session.parseBody(files)
        val temp = files["file"]?.let(::File)
        val dir = uploadDir
        if (temp == null || !temp.exists() || dir == null) {
            return html(formPage())
        }
        val name = session.parameters["file"]?.firstOrNull()
            ?.substringAfterLast('/')
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.takeIf { it.isNotBlank() }
            ?: "upload.tachibk"
        dir.mkdirs()
        val target = File(dir, name)
        temp.copyTo(target, overwrite = true)
        onUpload(target)
        return html(donePage())
    }

    private fun sendFile(mode: Mode.Download): Response {
        val response = newFixedLengthResponse(
            Response.Status.OK,
            "application/octet-stream",
            FileInputStream(mode.file),
            mode.file.length(),
        )
        response.addHeader("Content-Disposition", "attachment; filename=\"${mode.downloadName}\"")
        return response
    }

    private fun html(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun formPage(): String {
        val form = when (mode) {
            Mode.Text -> """
                <form method="post" action="/text?t=$token">
                  <label for="text">${page.inputLabel.escape()}</label>
                  <textarea id="text" name="text" rows="3" autofocus></textarea>
                  <button type="submit">${page.submit.escape()}</button>
                </form>
            """.trimIndent()
            Mode.Upload -> """
                <form method="post" action="/upload?t=$token" enctype="multipart/form-data">
                  <label for="file">${page.inputLabel.escape()}</label>
                  <input id="file" type="file" name="file" accept=".tachibk,.proto.gz,application/octet-stream">
                  <button type="submit">${page.submit.escape()}</button>
                </form>
            """.trimIndent()
            is Mode.Download -> """
                <a class="button" href="/download?t=$token">${page.download.escape()}</a>
            """.trimIndent()
        }
        return template("<h1>${page.title.escape()}</h1><p>${page.description.escape()}</p>$form")
    }

    private fun donePage(): String = template("<h1>${page.title.escape()}</h1><p>${page.sent.escape()}</p>")

    private fun template(content: String): String = """
        <!doctype html>
        <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Yomira</title>
        <style>
          body{font-family:system-ui,sans-serif;margin:0;padding:24px;background:#1a123d;color:#fff}
          h1{font-size:1.4em} label{display:block;margin:16px 0 8px}
          textarea,input{width:100%;box-sizing:border-box;padding:12px;font-size:1em;border-radius:8px;border:0}
          button,.button{display:inline-block;margin-top:16px;padding:12px 20px;font-size:1em;border:0;
            border-radius:24px;background:#ffc857;color:#1a123d;text-decoration:none}
        </style></head><body>$content</body></html>
    """.trimIndent()

    private fun String.escape(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private companion object {
        const val TOKEN_BYTES = 12
        const val MAX_TEXT_LENGTH = 4096
        const val MAX_UPLOAD_BYTES = 200L * 1024 * 1024
    }
}
