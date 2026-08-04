package com.example.pra.ui.scene

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 每帧变量集合 (渲染器逐帧填充, 表达式求值复用栈, 零分配)
 *
 * 变量: t=当前行进度(0..1) pos=播放位置(ms) time=系统时间(ms)
 *       dur=当前行时长(ms) playing=是否播放 w=屏宽 h=屏高
 *       alpha=进出场透明度(0..1) offsetY=进出场垂直偏移(px)
 */
class FrameVars {
    val values = FloatArray(VAR_COUNT)

    private val stack = FloatArray(64)

    fun set(varIndex: Int, v: Float) {
        values[varIndex] = v
    }

    fun eval(expr: AnimValue): Float {
        val program = when (expr) {
            is AnimValue.Const -> return expr.v
            is AnimValue.Expr -> expr.program
                ?: ExprEvaluator.parse(expr.source)?.also { expr.program = it }
                ?: return 0f
        }
        return ExprEvaluator.eval(program, this, stack)
    }

    companion object {
        const val VAR_COUNT = 9

        const val T = 0
        const val POS = 1
        const val TIME = 2
        const val DUR = 3
        const val PLAYING = 4
        const val W = 5
        const val H = 6
        const val ALPHA = 7
        const val OFFSET_Y = 8

        private val VAR_NAMES = mapOf(
            "t" to T, "pos" to POS, "time" to TIME, "dur" to DUR,
            "playing" to PLAYING, "w" to W, "h" to H, "alpha" to ALPHA, "offsetY" to OFFSET_Y
        )

        fun varIndex(name: String): Int = VAR_NAMES[name] ?: -1
    }
}

/**
 * 小型表达式引擎: 解析一次编译为指令流, 每帧用预分配栈机求值 (零分配、无反射)。
 *
 * 支持: + - * / % 一元负号 括号
 * 函数: sin cos tan sqrt abs floor ceil frac min max pow clamp lerp map smoothstep
 */
object ExprEvaluator {

    // 指令码
    private const val PUSH_CONST = 0
    private const val PUSH_VAR = 1
    private const val ADD = 2
    private const val SUB = 3
    private const val MUL = 4
    private const val DIV = 5
    private const val MOD = 6
    private const val NEG = 7
    private const val CALL = 8

    // 函数 id
    private const val FN_SIN = 0
    private const val FN_COS = 1
    private const val FN_TAN = 2
    private const val FN_SQRT = 3
    private const val FN_ABS = 4
    private const val FN_FLOOR = 5
    private const val FN_CEIL = 6
    private const val FN_FRAC = 7
    private const val FN_MIN = 8
    private const val FN_MAX = 9
    private const val FN_POW = 10
    private const val FN_CLAMP = 11
    private const val FN_LERP = 12
    private const val FN_MAP = 13
    private const val FN_SMOOTHSTEP = 14

    private val FUNCTIONS = mapOf(
        "sin" to FN_SIN, "cos" to FN_COS, "tan" to FN_TAN, "sqrt" to FN_SQRT,
        "abs" to FN_ABS, "floor" to FN_FLOOR, "ceil" to FN_CEIL, "frac" to FN_FRAC,
        "min" to FN_MIN, "max" to FN_MAX, "pow" to FN_POW,
        "clamp" to FN_CLAMP, "lerp" to FN_LERP, "map" to FN_MAP,
        "smoothstep" to FN_SMOOTHSTEP
    )

    class Program internal constructor(
        val code: IntArray,
        val consts: FloatArray
    )

    fun parse(source: String): Program? = try {
        Parser(source).parse()
    } catch (e: Exception) {
        null
    }

    fun eval(program: Program, vars: FrameVars, stack: FloatArray): Float {
        var sp = 0
        var ip = 0
        val code = program.code
        val consts = program.consts
        while (ip < code.size) {
            when (code[ip++]) {
                PUSH_CONST -> stack[sp++] = consts[code[ip++]]
                PUSH_VAR -> stack[sp++] = vars.values[code[ip++]]
                ADD -> { sp--; stack[sp - 1] += stack[sp] }
                SUB -> { sp--; stack[sp - 1] -= stack[sp] }
                MUL -> { sp--; stack[sp - 1] *= stack[sp] }
                DIV -> { sp--; stack[sp - 1] /= stack[sp] }
                MOD -> { sp--; stack[sp - 1] %= stack[sp] }
                NEG -> stack[sp - 1] = -stack[sp - 1]
                CALL -> sp = evalCall(code[ip++], stack, sp)
            }
        }
        return stack[0]
    }

    private fun evalCall(fn: Int, stack: FloatArray, inSp: Int): Int {
        var sp = inSp
        when (fn) {
            FN_SIN -> stack[sp - 1] = sin(stack[sp - 1])
            FN_COS -> stack[sp - 1] = cos(stack[sp - 1])
            FN_TAN -> stack[sp - 1] = tan(stack[sp - 1])
            FN_SQRT -> stack[sp - 1] = sqrt(stack[sp - 1])
            FN_ABS -> stack[sp - 1] = abs(stack[sp - 1])
            FN_FLOOR -> stack[sp - 1] = floor(stack[sp - 1])
            FN_CEIL -> stack[sp - 1] = ceil(stack[sp - 1])
            FN_FRAC -> stack[sp - 1] = stack[sp - 1] - floor(stack[sp - 1])

            FN_MIN -> {
                val b = stack[sp - 1]
                val a = stack[sp - 2]
                sp--
                stack[sp - 1] = min(a, b)
            }
            FN_MAX -> {
                val b = stack[sp - 1]
                val a = stack[sp - 2]
                sp--
                stack[sp - 1] = max(a, b)
            }
            FN_POW -> {
                val b = stack[sp - 1]
                val a = stack[sp - 2]
                sp--
                stack[sp - 1] = a.pow(b)
            }
            FN_CLAMP -> {
                val hi = stack[sp - 1]
                val lo = stack[sp - 2]
                val v = stack[sp - 3]
                sp -= 2
                stack[sp - 1] = v.coerceIn(lo, hi)
            }
            FN_LERP -> {
                val f = stack[sp - 1]
                val b = stack[sp - 2]
                val a = stack[sp - 3]
                sp -= 2
                stack[sp - 1] = a + (b - a) * f
            }
            FN_MAP -> {
                val oMax = stack[sp - 1]
                val oMin = stack[sp - 2]
                val iMax = stack[sp - 3]
                val iMin = stack[sp - 4]
                val v = stack[sp - 5]
                sp -= 4
                stack[sp - 1] = if (abs(iMax - iMin) < 0.00001f) oMin
                else (v - iMin) * (oMax - oMin) / (iMax - iMin) + oMin
            }
            FN_SMOOTHSTEP -> {
                val v = stack[sp - 1]
                val maxV = stack[sp - 2]
                val minV = stack[sp - 3]
                sp -= 2
                val x = ((v - minV) / (maxV - minV)).coerceIn(0f, 1f)
                stack[sp - 1] = x * x * (3f - 2f * x)
            }
        }
        return sp
    }

    private fun arityOf(fn: Int): Int = when (fn) {
        FN_SIN, FN_COS, FN_TAN, FN_SQRT, FN_ABS, FN_FLOOR, FN_CEIL, FN_FRAC -> 1
        FN_MIN, FN_MAX, FN_POW -> 2
        FN_CLAMP, FN_LERP, FN_SMOOTHSTEP -> 3
        FN_MAP -> 5
        else -> 0
    }

    /** 递归下降解析: 表达式 → 指令流 */
    private class Parser(private val src: String) {
        private var pos = 0
        private val code = mutableListOf<Int>()
        private val consts = mutableListOf<Float>()

        fun parse(): Program {
            parseExpr()
            skipWs()
            if (pos != src.length) fail("unexpected character at $pos")
            return Program(code.toIntArray(), consts.toFloatArray())
        }

        private fun parseExpr() {
            parseTerm()
            while (true) {
                skipWs()
                when (peek()) {
                    '+' -> { pos++; parseTerm(); emit(ADD) }
                    '-' -> { pos++; parseTerm(); emit(SUB) }
                    else -> return
                }
            }
        }

        private fun parseTerm() {
            parseFactor()
            while (true) {
                skipWs()
                when (peek()) {
                    '*' -> { pos++; parseFactor(); emit(MUL) }
                    '/' -> { pos++; parseFactor(); emit(DIV) }
                    '%' -> { pos++; parseFactor(); emit(MOD) }
                    else -> return
                }
            }
        }

        private fun parseFactor() {
            skipWs()
            val c = peek()
            when {
                c == '-' -> { pos++; parseFactor(); emit(NEG) }
                c == '(' -> {
                    pos++
                    parseExpr()
                    skipWs()
                    if (peek() != ')') fail("expected ')'")
                    pos++
                }
                c.isDigit() || c == '.' -> parseNumber()
                c == '_' || c in 'a'..'z' || c in 'A'..'Z' -> parseIdent()
                else -> fail("unexpected character '$c'")
            }
        }

        private fun parseNumber() {
            val start = pos
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
            if (pos == start) fail("invalid number")
            consts.add(src.substring(start, pos).toFloatOrNull() ?: fail("invalid number"))
            emit(PUSH_CONST)
            emit(consts.size - 1)
        }

        private fun parseIdent() {
            val start = pos
            while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_')) pos++
            val name = src.substring(start, pos)
            skipWs()
            if (peek() == '(') {
                pos++
                var argCount = 0
                skipWs()
                if (peek() != ')') {
                    while (true) {
                        parseExpr()
                        argCount++
                        skipWs()
                        if (peek() == ',') { pos++; skipWs(); continue }
                        break
                    }
                }
                if (peek() != ')') fail("expected ')'")
                pos++
                val fnId = FUNCTIONS[name] ?: fail("unknown function '$name'")
                if (arityOf(fnId) != argCount) fail("function '$name' expects ${arityOf(fnId)} args, got $argCount")
                emit(CALL)
                emit(fnId)
            } else {
                val idx = FrameVars.varIndex(name)
                if (idx < 0) fail("unknown variable '$name'")
                emit(PUSH_VAR)
                emit(idx)
            }
        }

        private fun peek(): Char = if (pos < src.length) src[pos] else '\u0000'

        private fun skipWs() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun emit(op: Int) {
            code.add(op)
        }

        private fun fail(msg: String): Nothing = throw IllegalArgumentException(msg)
    }
}
