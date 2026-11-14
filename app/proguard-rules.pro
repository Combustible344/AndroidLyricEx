# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ============================================================
# 伪息屏场景引擎 (LuaJ) 的反射保活
#
# 场景脚本通过 CoerceJavaToLua 把 Java 对象交给 Lua, 再以
# `canvas:drawText(...)` / `anim:lerp(...)` / `tools.loadFont(...)`
# 的方式按方法名反射调用 —— R8 无法静态发现这些引用, 必须保活,
# 否则 release 包里的场景会直接崩掉或画出空白。
# ============================================================

-keep class org.luaj.** { *; }
-dontwarn org.luaj.**

# canvas / anim / tools 门面与场景构建器: 类与全部 public 成员均不可裁剪/改名
-keep class com.example.pra.ui.scene.** { public *; }
-keepclassmembers class com.example.pra.ui.scene.** { public *; }

# 渲染器实现由 MainActivity 在运行期按类型切换
-keep class * implements com.example.pra.ui.scene.LyricCanvasRenderer { *; }

# 前台播放服务由 manifest 声明, 系统按类名拉起
-keep class com.example.pra.service.MusicService { *; }