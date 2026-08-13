package com.luoluo.luma.host

/**
 * 底座暴露给卡片的能力接口。
 *
 * 卡片模块只允许依赖这个接口，不能直接import storage.RoleDatabaseManager
 * 或者chat包里的类——这是"卡片间/卡片与底座之间零直接依赖"这条规则的具体落地方式。
 * 以后卡片一多，任何一张卡片能拿到的能力都得先在这个接口里加一个方法，
 * 而不是让卡片自己想办法去够底座内部的实现细节。
 *
 * 1d范围内先给存储这一个能力，够test-card验证用就行。
 * 以后AI通信、通知、文件访问这些能力也会陆续加进来，用法上跟这个一致：
 * 接口加方法 → CardHostApiImpl实现 → 卡片调用接口，不碰实现。
 */
interface CardHostApi {
    /**
     * 存一个小值，key建议加卡片自己的id前缀，比如"test-card:count"，
     * 避免不同卡片之间的key互相覆盖（数据库层不做强制隔离，靠约定）。
     */
    suspend fun setValue(key: String, value: String)

    /** 读一个值，没存过就返回null */
    suspend fun getValue(key: String): String?
}
