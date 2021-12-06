package xyz.xenondevs.nova.util

import java.math.BigDecimal

enum class MetricPrefix(exponent: Int, val prefixName: String, val prefixSymbol: String) {
    
    YOCTO(-24, "yocto", "y"),
    ZEPTO(-21, "zepto", "z"),
    ATTO(-18, "atto", "a"),
    FEMTO(-15, "femto", "f"),
    PICO(-12, "pico", "p"),
    NANO(-9, "nano", "n"),
    MICRO(-6, "micro", "μ"),
    MILLI(-3, "milli", "m"),
    CENTI(-2, "centi", "c"),
    DECI(-1, "deci", "d"),
    NONE(0, "", ""),
    DEKA(1, "deka", "da"),
    HECTO(2, "hecto", "h"),
    KILO(3, "kilo", "k"),
    MEGA(6, "mega", "M"),
    GIGA(9, "giga", "G"),
    TERA(12, "tera", "T"),
    PETA(15, "peta", "P"),
    EXA(18, "exa", "E"),
    ZETTA(21, "zetta", "Z"),
    YOTTA(24, "yotta", "Y");
    
    val number = BigDecimal("1E$exponent")
    
    companion object {
        
        private val VALUES = values()
        private val VALUES_REVERSED = values().reversedArray()
        
        fun findBestPrefix(number: BigDecimal, ignored: BooleanArray): Pair<BigDecimal, MetricPrefix> {
            if (number == BigDecimal.ZERO)
                return number to NONE
            
            var bestPrefix = NONE
            var newNumber = number
            for (prefix in VALUES_REVERSED) {
                if (!ignored[prefix.ordinal] && number >= prefix.number) {
                    bestPrefix = prefix
                    newNumber = number.divide(bestPrefix.number)
                    if (newNumber >= BigDecimal.ONE)
                        break
                }
            }
            return newNumber to bestPrefix
        }
        
        fun generateIgnoredArray(vararg ignoredPrefixes: MetricPrefix) =
            BooleanArray(VALUES.size) { VALUES[it] in ignoredPrefixes }
        
    }
    
}

