package me.marti.calprovexample

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import java.net.URLEncoder
import androidx.compose.ui.graphics.Color as ComposeColor

/** A **`List<T>`** grouped by values **`G`**, which are members of **`T`**. */
typealias GroupedList<G, T> = Map<G, List<T>>

/** A list that will always have *at least* 1 item. */
@Suppress("unused")
class NonEmptyList<T>(
    val first: T,
    val rest: List<T>
) {
    constructor(list: List<T>): this(list[0], list.drop(1))
    constructor(first: T, vararg rest: T): this(first, rest.asList())

    val size: Int
        get() = 1 + rest.size

    fun get(index: Int): T {
        return if (index == 0)
            first
        else
            rest[index]
    }
}

/** Similar to [List.map] but will fail and return **`NULL`** if `transform` returns **`NULL`** for any item in the list. */
fun <T, R> List<T>.tryMap(transform: (T) -> R?): List<R>? {
    return this.map {
        transform(it) ?: return null
    }
}

/** Get the file name for an URI that is a file or directory path.
 *
 * Returns `NULL` if the URI is not a path. */
fun Uri.fileName(): String? {
    val path = this.lastPathSegment ?: return null
    // first split the base of the path, then split the other components
    return path.split(':', limit = 2).last().split('/').last()
}
/** Append *[path] segments* to the end of the Uri path.
 *
 * Returns `NULL` if the URI is not a path. */
fun Uri.join(path: String): Uri? {
    if (this.lastPathSegment == null)
        return null
    // Prevent adding a second slash to the join point of the paths
    val slash = if (this.lastPathSegment!!.last() == '/') "" else "/"
    return "${this}${URLEncoder.encode("$slash$path", "utf-8")}".toUri()
}

fun fileNameWithoutExtension(fileName: String): String {
    val split = fileName.split('.')
    return if (split.size == 1)
        // File name has no extensions
        fileName
    else
        split.dropLast(1).joinToString(".")
}

data class Color(
    val r: UByte,
    val g: UByte,
    val b: UByte
) {
    /** Converts the color to a Hexadecimal representation of its RGB values.
     * The string always starts with a `#` for printing.*/
    override fun toString(): String = "#" + this.toHex()

    /** Converts the color to a Hexadecimal representation of its RGB values. */
    fun toHex(): String {
        var hex = ""
        hex += this.r.toString(16).padStart(2, '0')
        hex += this.g.toString(16).padStart(2, '0')
        hex += this.b.toString(16).padStart(2, '0')
        return hex
    }

    /** Convert this color to one that can be used by Jetpack Compose. */
    fun toColor(): ComposeColor {
        return ComposeColor(this.r.toInt(), this.g.toInt(), this.b.toInt())
    }
}

/** Creates a color from a 24bit Hexadecimal string (case insensitive).
 * If the string starts with a '#' it is ignored.
 * For example: `"ff0000"` -> `Color(255, 0, 0)`.
 *
 * Throws an error if the string contains any characters that are not `0 - 9`, `a - f`, or `A - F`.
 */
@Suppress("NAME_SHADOWING")
@Throws(NumberFormatException::class)
fun Color(hex: String): Color {
    // s could start with a '#'. In that case remove it.
    val hex = hex.getOrNull(0)?.let { first ->
        if (first == '#') {
            hex.drop(1)
        } else {
            hex
        }
    } ?: hex

    if (hex.length != 6) {
        throw NumberFormatException("Hex string should be an RGB hexadecimal (6 characters long)")
    }

    val r = hex.slice(0..1).toUByte(16)
    val g = hex.slice(2..3).toUByte(16)
    val b = hex.slice(4..5).toUByte(16)

    return Color(r, g, b)
}

/** Creates a color from the binary representation of an Integer.
 * This function assumes the bits represent an **ARGB** value.
 * The *Alpha* is ignored, so the color is converted to **RGB**.
 * Adapted from [https://stackoverflow.com/a/64168013](https://stackoverflow.com/a/64168013). */
fun Color(i: Int): Color {
    /** Apply a bit mask on the color value (i) to get the Byte at bytePos */
    fun mask(bytePos: Int): UByte = (i shr bytePos and 0xFF).toUByte()
    val progression = ((Int.SIZE_BITS - 8) downTo 0 step 8).iterator()

    progression.next() // Skip the Alpha value
    val r = mask(progression.next())
    val g = mask(progression.next())
    val b = mask(progression.next())

    return Color(r, g, b)
}

/** Creates a color from the `Color` class of androidx. */
fun Color(color: androidx.compose.ui.graphics.Color): Color {
    return Color(color.value.toInt())
}

// /** Extend class to add  */
// class OpenDirectory: ActivityResultContracts.OpenDocumentTree() {
//     override fun createIntent(context: Context, input: Uri?): Intent {
//         val intent = super.createIntent(context, input)
//         intent.putExtra(Intent.EXTRA)
//         return intent
//     }
// }