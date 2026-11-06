package com.mt.chat

import java.util.Calendar

object TimeFmt {

    fun rel(ts: Long): String {
        val now = System.currentTimeMillis()
        var diff = (now - ts) / 1000L
        if (diff < 0) diff = 0
        if (diff < 60) return "刚刚"
        if (diff < 3600) return (diff / 60).toString() + "分钟前"

        val d = Calendar.getInstance().apply { timeInMillis = ts }
        val n = Calendar.getInstance()
        val dayDiff = daysBetween(d, n)

        if (dayDiff == 0) return (diff / 3600).toString() + "小时前"
        if (dayDiff == 1) return "昨天"
        if (dayDiff < 7) return dayDiff.toString() + "天前"
        if (dayDiff < 30) return (dayDiff / 7).toString() + "星期前"
        if (dayDiff < 60) return "1个月前"
        return d.get(Calendar.YEAR).toString() + "年" +
                (d.get(Calendar.MONTH) + 1) + "月" +
                d.get(Calendar.DAY_OF_MONTH) + "日"
    }

    private fun daysBetween(a: Calendar, b: Calendar): Int {
        val x = Calendar.getInstance().apply {
            timeInMillis = a.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val y = Calendar.getInstance().apply {
            timeInMillis = b.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return ((y.timeInMillis - x.timeInMillis) / 86400000L).toInt()
    }
}
