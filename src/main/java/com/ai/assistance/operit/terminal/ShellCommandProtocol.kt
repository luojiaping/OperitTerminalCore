package com.ai.assistance.operit.terminal

import java.security.SecureRandom

internal object ShellCommandProtocol {
    private const val RECORD_SEPARATOR = '\u001e'
    private const val UNIT_SEPARATOR = '\u001f'
    private const val BEGIN_PREFIX = "OPERIT_SHELL_BEGIN:"
    private const val END_PREFIX = "OPERIT_SHELL_END:"
    private const val TOKEN_BYTES = 24
    private const val HEX_DIGITS = "0123456789abcdef"
    private val secureRandom = SecureRandom()

    fun newExecutionToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return buildString(TOKEN_BYTES * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0f])
            }
        }
    }

    fun beginMarker(token: String): String =
        "$RECORD_SEPARATOR$BEGIN_PREFIX$token$UNIT_SEPARATOR"

    fun endMarkerPrefix(token: String): String =
        "$RECORD_SEPARATOR$END_PREFIX$token:"

    fun markerSuffix(): String = UNIT_SEPARATOR.toString()

    fun wrap(command: String, token: String): String {
        val normalizedCommand = command.replace("\r\n", "\n").replace('\r', '\n')
        val beginPayload = "$BEGIN_PREFIX$token"
        val endPayload = "$END_PREFIX$token:"
        return buildString {
            // Keep the protocol subshell alive after Ctrl+C so it can report the child's status.
            append("(trap ':' INT; printf '\\036%s\\037\\n' ")
            append(singleQuote(beginPayload))
            append("; command sh -c ")
            append(singleQuote(normalizedCommand))
            append("; __operit_shell_exit=\$?; printf '\\n\\036%s%d\\037\\n' ")
            append(singleQuote(endPayload))
            append(" \"\$__operit_shell_exit\"; )")
        }
    }

    private fun singleQuote(value: String): String =
        "'${value.replace("'", "'\"'\"'")}'"
}
