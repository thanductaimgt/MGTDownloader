package com.mgt.downloader.utils

object PerlStringHelper {
    fun unescapePerl(str: String): String {
        /*
     * In contrast to fixing Java's broken regex charclasses,
     * this one need be no bigger, as unescaping shrinks the string
     * here, where in the other one, it grows it.
     */
        val newStr = StringBuffer(str.length)
        var sawBackslash = false
        var i = 0
        while (i < str.length) {
            var cp = str.codePointAt(i)
            if (str.codePointAt(i) > Character.MAX_VALUE.toInt()) {
                i++
                /****WE HATES UTF-16! WE HATES IT FOREVERSES!!! */
            }
            if (!sawBackslash) {
                if (cp == '\\'.toInt()) {
                    sawBackslash = true
                } else {
                    newStr.append(Character.toChars(cp))
                }
                i++
                continue  /* switch */
            }
            if (cp == '\\'.toInt()) {
                sawBackslash = false
                newStr.append('\\')
                newStr.append('\\')
                i++
                continue  /* switch */
            }
            when (cp.toChar()) {
                'r' -> newStr.append('\r')
                'n' -> newStr.append('\n')
//                'f' -> newstr.append('\f')
                'b' -> newStr.append("\\b")
                't' -> newStr.append('\t')
                'a' -> newStr.append('\u0007')
                'e' -> newStr.append('\u001b')
                'c' -> {
                    if (++i == str.length) {
                        die("trailing \\c")
                    }
                    cp = str.codePointAt(i)
                    /*
                 * don't need to grok surrogates, as next line blows them up
                 */if (cp > 0x7f) {
                        die("expected ASCII after \\c")
                    }
                    newStr.append(Character.toChars(cp xor 64))
                }
                '8', '9' -> run {
                    die("illegal octal digit")
                    --i
                    if (i + 1 == str.length) {
                        /* found \0 at end of string */
                        newStr.append(Character.toChars(0))
                        return@run /* switch */
                    }
                    i++
                    var digits = 0
                    var j: Int
                    j = 0
                    while (j <= 2) {
                        if (i + j == str.length) {
                            break /* for */
                        }
                        /* safe because will unread surrogate */
                        val ch = str[i + j].toInt()
                        if (ch < '0'.toInt() || ch > '7'.toInt()) {
                            break /* for */
                        }
                        digits++
                        j++
                    }
                    if (digits == 0) {
                        --i
                        newStr.append('\u0000')
                        return@run /* switch */
                    }
                    var value = 0
                    try {
                        value =
                            str.substring(i, i + digits).toInt(8)
                    } catch (nfe: NumberFormatException) {
                        die("invalid octal value for \\0 escape")
                    }
                    newStr.append(Character.toChars(value))
                    i += digits - 1
                }
                '1', '2', '3', '4', '5', '6', '7' -> run {
                    --i
                    if (i + 1 == str.length) {
                        newStr.append(Character.toChars(0))
                        return@run
                    }
                    i++
                    var digits = 0
                    var j: Int
                    j = 0
                    while (j <= 2) {
                        if (i + j == str.length) {
                            break
                        }
                        val ch = str[i + j].toInt()
                        if (ch < '0'.toInt() || ch > '7'.toInt()) {
                            break
                        }
                        digits++
                        j++
                    }
                    if (digits == 0) {
                        --i
                        newStr.append('\u0000')
                        return@run
                    }
                    var value = 0
                    try {
                        value =
                            str.substring(i, i + digits).toInt(8)
                    } catch (nfe: NumberFormatException) {
                        die("invalid octal value for \\0 escape")
                    }
                    newStr.append(Character.toChars(value))
                    i += digits - 1
                }
                '0' -> run {
                    if (i + 1 == str.length) {
                        newStr.append(Character.toChars(0))
                        return@run
                    }
                    i++
                    var digits = 0
                    var j: Int
                    j = 0
                    while (j <= 2) {
                        if (i + j == str.length) {
                            break
                        }
                        val ch = str[i + j].toInt()
                        if (ch < '0'.toInt() || ch > '7'.toInt()) {
                            break
                        }
                        digits++
                        j++
                    }
                    if (digits == 0) {
                        --i
                        newStr.append('\u0000')
                        return@run
                    }
                    var value = 0
                    try {
                        value =
                            str.substring(i, i + digits).toInt(8)
                    } catch (nfe: NumberFormatException) {
                        die("invalid octal value for \\0 escape")
                    }
                    newStr.append(Character.toChars(value))
                    i += digits - 1
                } /* end case '0' */
                'x' -> {
                    if (i + 2 > str.length) {
                        die("string too short for \\x escape")
                    }
                    i++
                    var saw_brace = false
                    if (str[i] == '{') {
                        /* ^^^^^^ ok to ignore surrogates here */
                        i++
                        saw_brace = true
                    }
                    var j: Int
                    j = 0
                    while (j < 8) {
                        if (!saw_brace && j == 2) {
                            break /* for */
                        }

                        /*
                     * ASCII test also catches surrogates
                     */
                        val ch = str[i + j].toInt()
                        if (ch > 127) {
                            die("illegal non-ASCII hex digit in \\x escape")
                        }
                        if (saw_brace && ch == '}'.toInt()) {
                            break /* for */
                        }
                        if (!(ch >= '0'.toInt() && ch <= '9'.toInt()
                                    ||
                                    ch >= 'a'.toInt() && ch <= 'f'.toInt()
                                    ||
                                    ch >= 'A'.toInt() && ch <= 'F'.toInt())
                        ) {
                            die(
                                String.format(
                                    "illegal hex digit #%d '%c' in \\x", ch, ch
                                )
                            )
                        }
                        j++
                    }
                    if (j == 0) {
                        die("empty braces in \\x{} escape")
                    }
                    var value = 0
                    try {
                        value = str.substring(i, i + j).toInt(16)
                    } catch (nfe: NumberFormatException) {
                        die("invalid hex value for \\x escape")
                    }
                    newStr.append(Character.toChars(value))
                    if (saw_brace) {
                        j++
                    }
                    i += j - 1
                }
                'u' -> {
                    if (i + 4 > str.length) {
                        die("string too short for \\u escape")
                    }
                    i++
                    var j: Int
                    j = 0
                    while (j < 4) {

                        /* this also handles the surrogate issue */if (str[i + j]
                                .toInt() > 127
                        ) {
                            die("illegal non-ASCII hex digit in \\u escape")
                        }
                        j++
                    }
                    var value = 0
                    try {
                        value = str.substring(i, i + j).toInt(16)
                    } catch (nfe: NumberFormatException) {
                        die("invalid hex value for \\u escape")
                    }
                    newStr.append(Character.toChars(value))
                    i += j - 1
                }
                'U' -> {
                    if (i + 8 > str.length) {
                        die("string too short for \\U escape")
                    }
                    i++
                    var j: Int
                    j = 0
                    while (j < 8) {

                        /* this also handles the surrogate issue */if (str[i + j]
                                .toInt() > 127
                        ) {
                            die("illegal non-ASCII hex digit in \\U escape")
                        }
                        j++
                    }
                    var value = 0
                    try {
                        value = str.substring(i, i + j).toInt(16)
                    } catch (nfe: NumberFormatException) {
                        die("invalid hex value for \\U escape")
                    }
                    newStr.append(Character.toChars(value))
                    i += j - 1
                }
                else -> {
                    newStr.append('\\')
                    newStr.append(Character.toChars(cp))
                }
            }
            sawBackslash = false
            i++
        }

        /* weird to leave one at the end */if (sawBackslash) {
            newStr.append('\\')
        }
        return newStr.toString()
    }

    private fun die(foa: String) {
        throw IllegalArgumentException(foa)
    }
}