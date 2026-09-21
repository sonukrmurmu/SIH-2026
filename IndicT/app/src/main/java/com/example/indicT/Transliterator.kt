package com.example.indicT

object Transliterator {

    // 2-character Aspirated Consonants
    private val aspiratedConsonants = mapOf(
        "ᱠᱷ" to "ख", "ᱜᱷ" to "घ", "ᱪᱷ" to "छ", "ᱡᱷ" to "झ",
        "ᱴᱷ" to "ठ", "ᱰᱷ" to "ढ", "ᱛᱷ" to "थ", "ᱫᱷ" to "ध",
        "ᱯᱷ" to "फ", "ᱵᱷ" to "भ", "ᱲᱷ" to "ढ़"
    )

    // 2-character Modified Vowels (with Gahla Tdd `ᱹ`)
    private val modifiedVowelsIndependent = mapOf(
        "ᱚᱹ" to "ऑ", "ᱟᱹ" to "अ", "ᱮᱹ" to "ऐ", "ᱳᱹ" to "औ"
    )

    private val modifiedMatras = mapOf(
        "ᱚᱹ" to "ॉ", "ᱟᱹ" to "", "ᱮᱹ" to "ै", "ᱳᱹ" to "ौ"
    )

    // 1-character Independent Vowels
    private val independentVowels = mapOf(
        "ᱚ" to "अ", "ᱟ" to "आ", "ᱤ" to "इ", "ᱩ" to "उ", "ᱮ" to "ए", "ᱳ" to "ओ"
    )

    // 1-character Vowel Matras
    private val matras = mapOf(
        "ᱚ" to "", "ᱟ" to "ा", "ᱤ" to "ि", "ᱩ" to "ु", "ᱮ" to "े", "ᱳ" to "ो"
    )

    // Single Consonants
    private val consonants = mapOf(
        "ᱛ" to "त", "ᱜ" to "ग", "ᱝ" to "ङ", "ᱞ" to "ल", "ᱠ" to "क",
        "ᱡ" to "ज", "ᱢ" to "म", "ᱣ" to "व", "ᱥ" to "स", "ᱦ" to "ह",
        "ᱧ" to "ञ", "ᱨ" to "र", "ᱪ" to "च", "ᱫ" to "द", "ᱬ" to "ण",
        "ᱭ" to "य", "ᱯ" to "प", "ᱰ" to "ड", "ᱱ" to "न", "ᱲ" to "ड़",
        "ᱴ" to "ट", "ᱵ" to "ब", "ᱶ" to "व"
    )

    fun olChikiToDevanagari(input: String): String {
        if (input.isBlank()) return "."

        val result = StringBuilder()
        var i = 0
        var prevWasConsonant = false

        while (i < input.length) {
            val twoChars = if (i + 1 < input.length) input.substring(i, i + 2) else ""
            val oneChar = input[i].toString()

            when {
                // 1. Check 2-char Aspirated Consonants first (e.g. ᱠᱷ -> ख)
                aspiratedConsonants.containsKey(twoChars) -> {
                    result.append(aspiratedConsonants[twoChars])
                    prevWasConsonant = true
                    i += 2
                }

                // 2. Check 2-char Modified Vowels (e.g. ᱚᱹ, ᱟᱹ, ᱮᱹ, ᱳᱹ)
                modifiedVowelsIndependent.containsKey(twoChars) -> {
                    if (prevWasConsonant) {
                        result.append(modifiedMatras[twoChars])
                    } else {
                        result.append(modifiedVowelsIndependent[twoChars])
                    }
                    prevWasConsonant = false
                    i += 2
                }

                // 3. Check 1-char Consonants
                consonants.containsKey(oneChar) -> {
                    result.append(consonants[oneChar])
                    prevWasConsonant = true
                    i++
                }

                // 4. Check 1-char Vowels
                independentVowels.containsKey(oneChar) -> {
                    if (prevWasConsonant) {
                        result.append(matras[oneChar])
                    } else {
                        result.append(independentVowels[oneChar])
                    }
                    prevWasConsonant = false
                    i++
                }

                // 5. Ol Chiki Special Modifiers
                oneChar == "ᱽ" -> {
                    // Deglottalizer / Halant
                    result.append("्")
                    prevWasConsonant = false
                    i++
                }
                oneChar == "ᱸ" || oneChar == "ᱺ" -> {
                    // Nasalization (Anusvara)
                    result.append("ं")
                    prevWasConsonant = false
                    i++
                }
                oneChar == "ᱻ" -> {
                    // Prolongation (Avagraha)
                    result.append("ऽ")
                    prevWasConsonant = false
                    i++
                }
                oneChar == "ᱹ" -> {
                    // Standalone Nukta
                    result.append("़")
                    prevWasConsonant = false
                    i++
                }
                oneChar == "ᱼ" -> {
                    // Syllable break
                    result.append(" ")
                    prevWasConsonant = false
                    i++
                }

                // 6. Whitespace and non-Ol Chiki punctuation/latin characters
                else -> {
                    result.append(oneChar)
                    if (oneChar.isBlank()) {
                        prevWasConsonant = false
                    }
                    i++
                }
            }
        }

        val finalString = result.toString().trim()
        return if (finalString.isBlank()) "." else finalString
    }
}