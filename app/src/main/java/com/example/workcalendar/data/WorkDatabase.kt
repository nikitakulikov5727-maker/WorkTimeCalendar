package com.example.workcalendar.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

tableName = "work_entries"
data class WorkEntry(
    autoGenerate = true val id: Long = 0,
    val dateEpochDay: Long,
    val tariffName: String,
    val minutes: Int,
    val rateKopecks: Long,
    val note: String = ""
) {
    fun calculateEarningsRub(): BigDecimal {
        val rateRub = BigDecimal(rateKopecks).divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
        val hours = BigDecimal(minutes).divide(BigDecimal(60), 6, RoundingMode.HALF_UP)
        return rateRub.multiply(hours).setScale(2, RoundingMode.HALF_UP)
    }
}

@Dao
interface WorkDao {
    "SELECT * FROM work_entries ORDER BY id ASC"
    fun getAllEntries(): Flow<List<WorkEntry»

    onConflict = OnConflictStrategy.REPLACE
    suspend fun insertAll(entries: List<WorkEntry>)

    "DELETE FROM work_entries WHERE dateEpochDay = :epochDay"
    suspend fun deleteByDay(epochDay: Long)
}

entities = [WorkEntry::class, version = 1, exportSchema = false]
abstract class WorkDatabase : RoomDatabase() {
    abstract fun workDao(): WorkDao

    companion object {
        @Volatile
        private var INSTANCE: WorkDatabase? = null

        fun getInstance(context: Context): WorkDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    WorkDatabase::class.java,
                    "work_calendar_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

fun splitNightShift(
    startDate: LocalDate,
    startTime: LocalTime,
    totalDurationMinutes: Int,
    tariffName: String,
    rateKopecks: Long
): List<WorkEntry> {
    if (totalDurationMinutes <= 0) return emptyList()

    val startMinuteOfDay = startTime.hour * 60 + startTime.minute
    val minutesUntilMidnight = 24 * 60 - startMinuteOfDay

    return if (totalDurationMinutes <= minutesUntilMidnight) {
        listOf(
            WorkEntry(
                dateEpochDay = startDate.toEpochDay(),
                tariffName = tariffName,
                minutes = totalDurationMinutes,
                rateKopecks = rateKopecks,
                note = "Смена"
            )
        )
    } else {
        val nextDayMinutes = totalDurationMinutes - minutesUntilMidnight
        listOf(
            WorkEntry(
                dateEpochDay = startDate.toEpochDay(),
                tariffName = tariffName,
                minutes = minutesUntilMidnight,
                rateKopecks = rateKopecks,
                note = "До 00:00"
            ),
            WorkEntry(
                dateEpochDay = startDate.plusDays(1).toEpochDay(),
                tariffName = tariffName,
                minutes = nextDayMinutes,
                rateKopecks = rateKopecks,
                note = "После 00:00"
            )
        )
    }
}

fun formatMinutes(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return String.format(Locale.US, "%d:%02d", h, m)
}

fun formatRub(amount: BigDecimal): String {
    return String.format(Locale("ru", "RU"), "%,.2f ₽", amount)
}

fun parseRateToKopecks(input: String): Long {
    val normalized = input.trim().replace(',', '.').replace(" ", "")
    val bd = normalized.toBigDecimalOrNull() ?: BigDecimal.ZERO
    return bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
}
