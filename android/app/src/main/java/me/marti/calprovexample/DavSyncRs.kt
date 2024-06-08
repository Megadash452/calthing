package me.marti.calprovexample

import android.util.Log

/** Rust functions that can be called from Java.
 * All the extern functions declared in this class are defined in `project root/rust/src/lib.rs` */
@Suppress("FunctionName")
object DavSyncRs {
    init {
        System.loadLibrary("davsync")
    }

    external fun initialize_sync_dir(fd: Int)

    private external fun import_file_internal(fileFd: Int, fileName: String, appDir: String): Byte
    fun importFileInternal(fileFd: Int, fileName: String, appDir: String): ImportFileResult {
        val calName = fileNameWithoutExtension(fileName)

        when (try {
            this.import_file_internal(fileFd, fileName, appDir).toInt()
        } catch (e: Exception) {
            Log.e(null, "Error importing file. Thrown exception:\n$e")
            return ImportFileResult.Error
        }) {
            1 -> {
                println("file '${fileName}' imported successfully")
                return ImportFileResult.Success(calName)
            }
            2 -> {
                println("'${fileName}' is already imported. Overwrite?")
                return ImportFileResult.FileExists(calName)
            }
            else -> throw IllegalStateException("*prowler sfx*")
        }
    }

    private external fun import_file_external(externalFileFd: Int, fileName: String, appDir: String)
    fun importFileExternal(externalFileFd: Int, fileName: String, appDir: String): Boolean {
        return try {
            this.import_file_external(externalFileFd, fileName, appDir)
            true
        } catch (e: Exception) {
            Log.e(null, "Error importing file to external storage. Thrown exception:\n$e")
            false
        }
    }

    external fun parse_file(appDir: String, fileName: String)
}

@Suppress("unused")
class RustPanic: RuntimeException()

/** Result from calling Native function [DavSyncRs.importFileInternal].
 *
 * [code] is the return code from the Native function.
 * `calName` is the name of the imported Calendar as it should appear in the Content Provider.
 * If [code] is:
 * * **`0`**, there was an error and an Exception was was thrown. TODO: might just put the error in the String without throwing.
 * * **`1`**, the file was imported successfully.
 * * **`2`**, an imported calendar with that name already exists, ask user to *overwrite* or *pick another name*. */
@Suppress("ConvertObjectToDataObject")
sealed class ImportFileResult {
    object Error: ImportFileResult()
    class Success(val calName: String): ImportFileResult()
    class FileExists(val calName: String): ImportFileResult()
}
