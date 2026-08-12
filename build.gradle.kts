// 根目录构建文件：只负责声明插件版本，不在这里应用
// 真正的 android {} 配置在 app/build.gradle.kts 里
plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    // 1c新增：Room数据库要用注解处理器生成代码，KSP版本要跟Kotlin版本(2.0.20)对上。
    // 如果编译报"找不到匹配的KSP版本"这类错，去KSP官方Release页面
    // (https://github.com/google/ksp/releases) 找对应2.0.20的最新patch号替换掉。
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
}