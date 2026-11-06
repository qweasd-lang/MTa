package com.mt.chat

object Models {
    data class Item(val id: String, val name: String, val vision: Boolean)

    val all: List<Item> = listOf(
        Item("glm-5.3-flashx", "GLM-5.3-Flashx", true),
        Item("glm-5.3-flash", "GLM-5.3-Flash", true),
        Item("glm-5.3", "GLM-5.3", true),
        Item("glm-5.2", "GLM-5.2", true),
        Item("glm-5.1", "GLM-5.1", true),
        Item("glm-5-turbo", "GLM-5-Turbo", true),
        Item("glm-5", "GLM-5", true),
        Item("glm-4.7", "GLM-4.7", true),
        Item("glm-4.6", "GLM-4.6", true),
        Item("glm-4.5", "GLM-4.5", true)
    )

    const val DEFAULT = "glm-4.6"

    fun find(id: String?): Item = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT }

    fun cap(id: String?): String = if (find(id).vision) "文字+图片" else "文字"

    fun label(id: String?): String = find(id).name + "（" + cap(id) + "）"
}
