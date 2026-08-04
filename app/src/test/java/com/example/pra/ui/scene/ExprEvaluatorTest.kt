package com.example.pra.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExprEvaluatorTest {

    private val vars = FrameVars()

    private fun eval(expr: String): Float {
        val program = ExprEvaluator.parse(expr)
        checkNotNull(program) { "parse failed: $expr" }
        return ExprEvaluator.eval(program, vars, FloatArray(64))
    }

    private fun setupVars() {
        vars.set(FrameVars.T, 0.5f)
        vars.set(FrameVars.POS, 1000f)
        vars.set(FrameVars.TIME, 2000f)
        vars.set(FrameVars.DUR, 2000f)
        vars.set(FrameVars.PLAYING, 1f)
        vars.set(FrameVars.W, 1080f)
        vars.set(FrameVars.H, 2400f)
        vars.set(FrameVars.ALPHA, 0.8f)
        vars.set(FrameVars.OFFSET_Y, -20f)
    }

    @Test
    fun `basic arithmetic and priority`() {
        setupVars()
        assertEquals(7f, eval("1 + 2 * 3"), 0.0001f)
        assertEquals(9f, eval("(1 + 2) * 3"), 0.0001f)
        assertEquals(-5f, eval("2 - 7"), 0.0001f)
        assertEquals(2.5f, eval("5 / 2"), 0.0001f)
        assertEquals(1f, eval("5 % 2"), 0.0001f)
        assertEquals(-3f, eval("-3"), 0.0001f)
        assertEquals(3f, eval("--3"), 0.0001f)
        assertEquals(1.5f, eval("1 + 2 / 4"), 0.0001f)
    }

    @Test
    fun `variables resolve per frame`() {
        setupVars()
        // w*0.5=540 + h/10=240 - 200 = 580
        assertEquals(580f, eval("w * 0.5 + h / 10 - 200"), 0.0001f)
        assertEquals(0.5f, eval("t"), 0.0001f)
        assertEquals(-20f, eval("offsetY"), 0.0001f)
        // 变量随 FrameVars 变化
        vars.set(FrameVars.T, 0.25f)
        assertEquals(0.25f, eval("t"), 0.0001f)
    }

    @Test
    fun `math functions`() {
        setupVars()
        assertEquals(1f, eval("sin(1.5707963)"), 0.0001f)
        assertEquals(2f, eval("sqrt(4)"), 0.0001f)
        assertEquals(5f, eval("abs(-5)"), 0.0001f)
        assertEquals(3f, eval("floor(3.7)"), 0.0001f)
        assertEquals(4f, eval("ceil(3.2)"), 0.0001f)
        assertEquals(5f, eval("min(5, 9)"), 0.0001f)
        assertEquals(9f, eval("max(5, 9)"), 0.0001f)
        assertEquals(8f, eval("pow(2, 3)"), 0.0001f)
        assertEquals(0.5f, eval("clamp(1.5, 0, 0.5)"), 0.0001f)
        assertEquals(15f, eval("lerp(10, 20, 0.5)"), 0.0001f)
        assertEquals(250f, eval("map(0.5, 0, 1, 0, 500)"), 0.0001f)
        assertEquals(0.5f, eval("smoothstep(0, 1, 0.5)"), 0.0001f)
        assertEquals(0.5f, eval("frac(3.5)"), 0.0001f)
    }

    @Test
    fun `nested calls and spaces`() {
        setupVars()
        assertEquals(1f, eval("clamp(lerp(0, 2, t), 0, 1)"), 0.0001f)
        // h/2=1200 + offsetY=-20 + w*0.5=540 = 1720
        assertEquals(1720f, eval(" h / 2 + offsetY + w * 0.5 "), 0.0001f)
        // min(t,1)=0.5, max(0.5,0.4)=0.5
        assertEquals(0.5f, eval("max(min(t, 1), 0.4)"), 0.0001f)
        // min(t,1)=0.5, max(0.5,0)=0.5
        assertEquals(0.5f, eval("max(min(t, 1), 0)"), 0.0001f)
    }

    @Test
    fun `parse failures return null`() {
        assertNull(ExprEvaluator.parse("1 +"))
        assertNull(ExprEvaluator.parse("foo"))
        assertNull(ExprEvaluator.parse("1 + unknown_var"))
        assertNull(ExprEvaluator.parse("sin(1, 2)"))
        assertNull(ExprEvaluator.parse("(1 + 2"))
        assertNull(ExprEvaluator.parse(""))
        assertNull(ExprEvaluator.parse("1 @ 2"))
    }

    @Test
    fun `parse failure expression degrades to zero`() {
        val expr = AnimValue.Expr("h / 2 + unknown")
        setupVars()
        assertEquals(0f, vars.eval(expr), 0.0001f)
        // 常量直接返回
        assertEquals(42f, vars.eval(AnimValue.Const(42f)), 0.0001f)
    }

    @Test
    fun `expression compiled once and reused`() {
        val expr = AnimValue.Expr("sin(t * 6.2831)")
        setupVars()
        vars.eval(expr)
        assertTrue(expr.program != null)
        val first = vars.eval(expr)
        vars.set(FrameVars.T, 0.25f)
        val second = vars.eval(expr)
        assertTrue(first != second)
        assertTrue(expr.program != null)
    }
}
