package com.audio.study.ffmpegdecoder.utils

import com.audio.study.ffmpegdecoder.R
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.roundToLong

//1秒, 单位:秒
const val ONE_SECOND_IN_SECONDS = 1L
//1分钟, 单位:秒
const val ONE_MINUTE_IN_SECONDS = ONE_SECOND_IN_SECONDS * 60
/**
 * 私信息里的时间显示规则
 * 24h内 ，显示 具体时间 eg：9:00 PM、10:32 AM(时间判断是否是当天，如果不是则按下列规则显示)
 * 24h-7d，显示 具体时间和日期：dd/mm/yyyy 7.5.9更改
 *
 * @param targetTime
 * @return
 */
fun getFormatTime(targetTime: Long, timeUnit: TimeUnit): String {
    var time = targetTime
    if (time == 0L)
        return ""
    val deviceLocale = Locale.getDefault()
    //  将时间全部转换成 millisecond
    if (timeUnit != TimeUnit.MILLISECONDS) {
        time = TimeUnit.MILLISECONDS.convert(time, timeUnit)
    }
    val curTime = System.currentTimeMillis()
    val showDate = Date(time)
    val curDate = Date(curTime)
    val sdf = SimpleDateFormat("yyyyMMdd", deviceLocale)

    if (sdf.format(showDate) == sdf.format(curDate)) {
        val dateFormat = SimpleDateFormat("hh:mm a", deviceLocale)
        return dateFormat.format(showDate)
    }
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", deviceLocale)
    return dateFormat.format(showDate)
}

/**
 * 有问题
 */
fun getFormatTime(targetTime: Long, timeUnit: TimeUnit, patten: String): String {
    var time = targetTime
    if (time == 0L)
        return ""
    val deviceLocale = Locale.getDefault()
    //  将时间全部转换成 millisecond
    if (timeUnit != TimeUnit.MILLISECONDS) {
        time = TimeUnit.MILLISECONDS.convert(time, timeUnit)
    }
    val showDate = Date(time)
    val dateFormat = SimpleDateFormat(patten, deviceLocale)
    return dateFormat.format(showDate)
}

fun getSecondsByMlsWithRound(milliSeconds: Long): Long {
    return (milliSeconds * 1.0f / 1000).roundToLong()
}

fun getSecondsByMlsWithFloor(milliSeconds: Long): Long {
    return floor(milliSeconds * 1.0 / 1000).toLong()
}

fun checkHourOfTime(hour: Int, targetTime: Long): Boolean {
    return System.currentTimeMillis() - targetTime > hour * 60 * 60 * 1000
}

/**
 * 将秒格式化
 * 目前只支持到分钟
 * 比如 30secs, 1min 1min20secs,2mins, 3mins40secs
 * @param timeInSecond 秒
 * @return 格式化后的时间
 */
fun formatSeconds(timeInSecond: Long): String {
    if (timeInSecond < ONE_MINUTE_IN_SECONDS) {
        //小于60秒
        return ResourceUtils.getString(R.string.time_format_seconds, timeInSecond.toString())
    }
    if (timeInSecond % ONE_MINUTE_IN_SECONDS == 0L) {
        //整的分钟数
        val minutes = timeInSecond / ONE_MINUTE_IN_SECONDS
        if (minutes == 1L) {
            //1分钟需要返回单数形式
            return ResourceUtils.getString(R.string.time_format_one_minute)
        }
        return ResourceUtils.getString(R.string.time_format_minutes, minutes.toString())
    }
    if (timeInSecond < (ONE_MINUTE_IN_SECONDS * 2)) {
        //少于2分钟,分钟数按单数形式
        return ResourceUtils.getString(R.string.time_format_one_minute_multi_seconds, (timeInSecond % ONE_MINUTE_IN_SECONDS).toString())
    }
    val minutes = timeInSecond / ONE_MINUTE_IN_SECONDS
    val seconds = timeInSecond % ONE_MINUTE_IN_SECONDS
    return ResourceUtils.getString(R.string.time_format_multi_minutes_multi_seconds, minutes.toString(), seconds.toString())
}

/**
 * 将秒格式化
 * 目前只支持到分钟
 * 比如 30secs, 1min 1min20secs,2mins, 3mins40secs
 * @param timeInSecond 秒
 * @return 格式化后的时间
 */
fun formatSecond(timeInSecond: Long): String {
    if (timeInSecond < ONE_MINUTE_IN_SECONDS) {
        //小于60秒
        return ResourceUtils.getString(R.string.time_format_seconds, timeInSecond.toString())
    }
    if (timeInSecond % ONE_MINUTE_IN_SECONDS == 0L) {
        //整的分钟数
        val minutes = timeInSecond / ONE_MINUTE_IN_SECONDS
        return ResourceUtils.getString(R.string.time_format_minute, minutes.toString())
    }
    val minutes = timeInSecond / ONE_MINUTE_IN_SECONDS
    val seconds = timeInSecond % ONE_MINUTE_IN_SECONDS
    return ResourceUtils.getString(R.string.time_format_multi_minute_multi_second, minutes.toString(), seconds.toString())
}

fun convertCountdown(mills: Long): String {
    val hours = formatTimeNum(TimeUnit.MILLISECONDS.toHours(mills))
    val minutes = formatTimeNum(TimeUnit.MILLISECONDS.toMinutes(mills) % TimeUnit.HOURS.toMinutes(1))
    val seconds = formatTimeNum(TimeUnit.MILLISECONDS.toSeconds(mills) % TimeUnit.MINUTES.toSeconds(1))
    return  "$hours:$minutes:$seconds"
}

fun getAge(birthDay: Date?): Int {
    val cal = Calendar.getInstance()
    if (cal.before(birthDay)) {
        return 0
    }
    val yearNow = cal[Calendar.YEAR]
    val monthNow = cal[Calendar.MONTH]
    val dayOfMonthNow = cal[Calendar.DAY_OF_MONTH]
    cal.time = birthDay
    val yearBirth = cal[Calendar.YEAR]
    val monthBirth = cal[Calendar.MONTH]
    val dayOfMonthBirth = cal[Calendar.DAY_OF_MONTH]
    var age = yearNow - yearBirth
    if (monthNow <= monthBirth) {
        if (monthNow == monthBirth) {
            if (dayOfMonthNow < dayOfMonthBirth) {
                age-- //当前日期在生日之前，年龄减一
            }
        } else {
            age-- //当前月份在生日之前，年龄减一
        }
    }
    return age
}

//数字格式化，将 0~9 的时间转换为 00~09
fun formatTimeNum(timeNum: Long): String? {
    return if (timeNum < 10) "0$timeNum" else timeNum.toString()
}
