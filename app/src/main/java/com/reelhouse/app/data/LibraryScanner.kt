package com.reelhouse.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Walks a folder picked with the Android folder picker (SAF tree) and lists video files. */
object LibraryScanner {
    data class Found(val uri: Uri, val name: String, val size: Long)

    private val videoExts = setOf("mp4", "m4v", "mkv", "avi", "mov", "webm", "wmv", "3gp", "mpg", "mpeg")

    fun folderName(ctx: Context, tree: Uri): String {
        return try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            ctx.contentResolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: "Folder"
        } catch (e: Exception) {
            "Folder"
        }
    }

    fun scan(ctx: Context, tree: Uri): List<Found> {
        val out = ArrayList<Found>()
        val resolver = ctx.contentResolver
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        fun walk(docId: String, depth: Int) {
            if (depth > 12) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val dirs = ArrayList<String>()
            resolver.query(children, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2) ?: ""
                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    if (name.startsWith(".")) continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        dirs.add(id)
                    } else {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (mime.startsWith("video/") || ext in videoExts) {
                            out.add(Found(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, size))
                        }
                    }
                }
            }
            for (d in dirs) walk(d, depth + 1)
        }

        walk(DocumentsContract.getTreeDocumentId(tree), 0)
        return out
    }
}
