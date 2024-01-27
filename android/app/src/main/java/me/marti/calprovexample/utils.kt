package me.marti.calprovexample


class Color(
    val R: UByte,
    val G: UByte,
    val B: UByte
) {
    /** Converts the color to a Hexadecimal representation of its RGB values.
     * The string always starts with a `#`.*/
    override fun toString(): String {
        var hex = "#"
        hex += this.R.toString(16).padStart(2, '0')
        hex += this.G.toString(16).padStart(2, '0')
        hex += this.B.toString(16).padStart(2, '0')
        return hex
    }
}

/** Creates a color from a 24bit Hexadecimal string (case insensitive).
 * If the string starts with a '#' it is ignored.
 * For example: `"ff0000"` -> `Color(255, 0, 0)`.
 *
 * Throws an error if the string contains any characters that are not `0 - 9`, `a - f`, or `A - F`.
 */
@Suppress("NAME_SHADOWING")
fun Color(hex: String): Color {
    // s could start with a '#'. In that case remove it.
    val hex = if (hex[0] == '#') {
        hex.drop(1)
    } else {
        hex
    }

    if (hex.length > 6) {
        throw NumberFormatException("hex string should be an RGB hexadecimal (6 characters)")
    }

    val r = hex.slice(0..1).toUByte(16)
    val g = hex.slice(2..3).toUByte(16)
    val b = hex.slice(4..5).toUByte(16)

    return Color(r, g, b)
}

/** Creates a color from the binary representation of an Integer.
 * This function assumes the bits represent an **ARGB** value.
 * The *Alpha* is ignored, so the color is converted to **RGB**.
 */
fun Color(i: Int): Color {
    // Drop the 2 characters of the Alpha channel.
    return Color(hexString(i).drop(2))
}

/** Convert an integer to a string of its hexadecimal representation.
 * Adapted from [https://stackoverflow.com/a/64168013](https://stackoverflow.com/a/64168013). */
private fun hexString(i: Int): String {
    return IntProgression
        // Operate on each byte of the Int
        .fromClosedRange(rangeStart = Int.SIZE_BITS - 8, rangeEnd = 0, step = -8)
        // Convert each byte to a hex (base 16) string (2 chars)
        .joinToString("") { bytePos ->
            (i shr bytePos and 0xFF).toString(16).padStart(2, '0')
        }
}