package com.example.gunpladecal.app.util

object Base62 {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val BASE = 62L

    fun encode(n: Long): String {
        if (n == 0L) return "0"
        val sb = StringBuilder()
        var v = n
        while (v > 0) {
            sb.append(ALPHABET[(v % BASE).toInt()])
            v /= BASE
        }
        return sb.reverse().toString()
    }

    fun decode(s: String): Long {
        var result = 0L
        for (c in s) {
            val idx = ALPHABET.indexOf(c)
            require(idx >= 0) { "Invalid base62 character: $c" }
            result = result * BASE + idx
        }
        return result
    }
}
