package pk.edunexa.app.attendance

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ArrivalPolicy {
    private val zone = ZoneId.of("Asia/Karachi")
    fun validate(settings: Map<String, Any?>) {
        if(settings["autoLateEnabled"] != true) return
        require(settings["schoolStartTime"]?.toString()?.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) == true) { "School start time must be HH:mm (24-hour), e.g. 08:00." }
        val grace = settings["lateGraceMinutes"] as? Number
        require(grace != null && grace.toDouble().isFinite() && grace.toDouble() % 1.0 == 0.0 && grace.toInt() in 0..180) { "Late grace must be a whole number from 0 to 180 minutes." }
        require(LocalTime.parse(settings["schoolStartTime"].toString()).toSecondOfDay()/60 + grace.toInt() < 1440) { "Start time plus grace must stay within the school day." }
    }
    fun fields(settings: Map<String, Any?>, instant: Instant = Instant.now()): Map<String, Any?> {
        validate(settings)
        val local=instant.atZone(zone)
        val enabled=settings["autoLateEnabled"] == true
        val deadline=if(enabled) LocalTime.parse(settings["schoolStartTime"].toString()).plusMinutes((settings["lateGraceMinutes"] as Number).toLong()) else null
        val late=deadline != null && local.toLocalTime().isAfter(deadline)
        return mapOf("status" to if(late) "Late" else "Present", "arrivalAt" to instant.toString(), "arrivalTime" to local.format(DateTimeFormatter.ofPattern("HH:mm:ss")), "arrivalDate" to local.toLocalDate().toString(), "arrivalTimeZone" to zone.id, "arrivalClockSource" to "staffDevice", "schoolStartTimeAtScan" to if(enabled) settings["schoolStartTime"] else "", "lateGraceMinutesAtScan" to if(enabled) settings["lateGraceMinutes"] else 0)
    }
}
