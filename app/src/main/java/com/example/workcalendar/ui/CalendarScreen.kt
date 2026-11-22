package com.example.workcalendar.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.workcalendar.data.*
import com.example.workcalendar.sync.GoogleDriveSyncManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class EditableTariffRow(
    val id: Long = 0,
    val name: String,
    val hoursText: String,
    val minutesText: String,
    val rateText: String,
    val note: String = ""
)

@Composable
fun CalendarScreen(
    dao: WorkDao,
    syncManager: GoogleDriveSyncManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allEntries by dao.getAllEntries().collectAsState(initial = emptyList())
    val savedTemplates by dao.getTariffTemplates().collectAsState(initial = emptyList())
    val syncState by syncManager.uiState.collectAsState()

    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDayForEdit by remember { mutableStateOf<LocalDate?>(null) }
    var showCloudDialog by remember { mutableStateOf(false) }

    var isRangeMode by remember { mutableStateOf(false) }
    var rangeStart by remember { mutableStateOf<LocalDate?>(null) }
    var rangeEnd by remember { mutableStateOf<LocalDate?>(null) }

    var hasInitializedData by remember { mutableStateOf(false) }
    LaunchedEffect(allEntries, savedTemplates) {
        if (!hasInitializedData) {
            hasInitializedData = true
            return@LaunchedEffect
        }
        if (syncState.account != null && syncState.autoSyncEnabled && !syncState.isBusy) {
            delay(800)
            syncManager.backupToDrive(
                entries = allEntries,
                templates = savedTemplates,
                silent = true
            )
        }
    }

    val entriesByDay = remember(allEntries) {
        allEntries.groupBy { LocalDate.ofEpochDay(it.dateEpochDay) }
    }

    val monthEntries = remember(allEntries, currentMonth) {
        allEntries.filter {
            val d = LocalDate.ofEpochDay(it.dateEpochDay)
            d.year == currentMonth.year && d.month == currentMonth.month
        }
    }
    val yearEntries = remember(allEntries, currentMonth) {
        allEntries.filter { LocalDate.ofEpochDay(it.dateEpochDay).year == currentMonth.year }
    }

    val workedDaysCount = monthEntries.map { it.dateEpochDay }.distinct().count()
    val monthTotalMinutes = monthEntries.sumOf { it.minutes }
    val monthTotalMoney = monthEntries.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.calculateEarningsRub()) }
    val yearTotalMoney = yearEntries.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.calculateEarningsRub()) }

    val rangeStats = remember(allEntries, rangeStart, rangeEnd) {
        if (rangeStart != null && rangeEnd != null) {
            val start = minOf(rangeStart!!, rangeEnd!!)
            val end = maxOf(rangeStart!!, rangeEnd!!)
            val filtered = allEntries.filter {
                val d = LocalDate.ofEpochDay(it.dateEpochDay)
                !d.isBefore(start) && !d.isAfter(end)
            }
            val mins = filtered.sumOf { it.minutes }
            val money = filtered.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.calculateEarningsRub()) }
            Triple(start, end, Pair(mins, money))
        } else null
    }

    Scaffold(
        containerColor = Color(0xFF141414),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color(0xFF1C1C1C))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = currentMonth.year.toString(),
                        color = Color(0xFFB0B0B0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { showCloudDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (syncState.account != null) Color(0xFF1E3A5F) else Color(0xFF2C2C2C),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = if (syncState.account != null) Icons.Default.CloudDone else Icons.Default.Cloud,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (syncState.account != null) "Облако" else "Войти",
                                fontSize = 13.sp
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                isRangeMode = !isRangeMode
                                if (!isRangeMode) {
                                    rangeStart = null
                                    rangeEnd = null
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isRangeMode) Color(0xFF2E7D32) else Color(0xFF2C2C2C),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isRangeMode) "Период: ВКЛ" else "Период", fontSize = 13.sp)
                        }
                    }
                }Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                    Text(
                        text = currentMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
                            .replaceFirstChar { it.uppercase() },
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Вперёд", tint = Color.White)
                    }
                }

                val daysOfWeek = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    daysOfWeek.forEach { day ->
                        Text(
                            text = day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            color = Color(0xFFAAAAAA),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(Color(0xFF222222))
                    .padding(14.dp)
            ) {
                if (isRangeMode) {
                    if (rangeStats != null) {
                        val (s, e, totals) = rangeStats
                        val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                        Text(
                            text = "Период: ${s.format(fmt)} — ${e.format(fmt)}",
                            color = Color(0xFF81C784),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        SummaryRow("Часов за период:", formatMinutes(totals.first))
                        SummaryRow("Доход за период:", formatRub(totals.second), highlight = true)
                    } else {
                        Text(
                            text = if (rangeStart == null)
                                "Нажмите дату начала периода в календаре"
                            else
                                "Начало: ${rangeStart!!.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}. Нажмите дату конца",
                            color = Color(0xFFFFD54F),
                            fontSize = 14.sp
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF383838))
                }

                SummaryRow("Дней отработано", workedDaysCount.toString())
                SummaryRow("Часов за месяц", formatMinutes(monthTotalMinutes))
                SummaryRow("Доход за месяц", formatRub(monthTotalMoney), highlight = true)
                SummaryRow("Доход за год", formatRub(yearTotalMoney))
            }
        }
    ) { innerPadding ->
        val daysGrid = remember(currentMonth) { buildCalendarDays(currentMonth) }
        val today = LocalDate.now()

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF141414))
        ) {
            items(daysGrid.size) { idx ->
                val date = daysGrid[idx]
                val isCurrentMonth = date.month == currentMonth.month
                val dayEntries = entriesByDay[date].orEmpty()
                val totalMinutes = dayEntries.sumOf { it.minutes }
                val totalDayMoney = dayEntries.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.calculateEarningsRub()) }

                val isSelectedInRange = remember(date, rangeStart, rangeEnd) {
                    if (rangeStart == null) false
                    else if (rangeEnd == null) date == rangeStart
                    else {
                        val s = minOf(rangeStart!!, rangeEnd!!)
                        val e = maxOf(rangeStart!!, rangeEnd!!)
                        !date.isBefore(s) && !date.isAfter(e)
                    }
                }

                val cellBg = when {
                    isSelectedInRange -> Color(0xFF1B5E20)
                    isCurrentMonth -> Color(0xFF212121)
                    else -> Color(0xFF171717)
                }

                val borderColor = if (date == today) Color(0xFFE53935) else Color(0xFF2E2E2E)
                val borderWidth = if (date == today) 1.5.dp else 0.5.dp

                Box(
                    modifier = Modifier
                        .height(78.dp)
                        .background(cellBg)
                        .border(borderWidth, borderColor)
                        .clickable {
                            if (isRangeMode) {
                                when {
                                    rangeStart == null || (rangeStart != null && rangeEnd != null) -> {
                                        rangeStart = date
                                        rangeEnd = null
                                    }
                                    else -> {
                                        rangeEnd = date
                                    }
                                }
                            } else {
                                selectedDayForEdit = date
                            }
                        }
                        .padding(4.dp)
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        modifier = Modifier.align(Alignment.TopEnd),
                        color = if (isCurrentMonth) Color(0xFFCCCCCC) else Color(0xFF555555),
                        fontSize = 12.sp
                    )

                    if (totalMinutes > 0) {
                        val hoursColor = if (totalMinutes > 480) Color(0xFF64B5F6) else Color(0xFFE57373)
                        val hoursLabel = if (totalMinutes % 60 == 0) {
                            "${totalMinutes / 60}"
                        } else {
                            formatMinutes(totalMinutes)
                        }

                        Text(
                            text = hoursLabel,
                            modifier = Modifier.align(Alignment.Center),
                            color = hoursColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = totalDayMoney.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                            modifier = Modifier.align(Alignment.BottomStart),
                            color = Color(0xFF66BB6A),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    if (showCloudDialog) {
        val activity = context as? ActivityAlertDialog(
            onDismissRequest = { showCloudDialog = false },
            containerColor = Color(0xFF262626),
            title = {
                Text(
                    text = "Синхронизация с Google Диском",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val account = syncState.account
                    if (account != null) {
                        Text(
                            text = "Аккаунт: ${account.displayName ?: account.email}",
                            color = Color(0xFF81C784),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = account.email,
                            color = Color(0xFFB0B0B0),
                            fontSize = 12.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Автосинхронизация", color = Color.White, fontSize = 14.sp)
                            Switch(
                                checked = syncState.autoSyncEnabled,
                                onCheckedChange = { syncManager.setAutoSync(it) }
                            )
                        }

                        syncState.lastSyncLabel?.let { label ->
                            Text(
                                text = "Последняя синхронизация: $label",
                                color = Color(0xFF90CAF9),
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    syncManager.backupToDrive(
                                        entries = allEntries,
                                        templates = savedTemplates
                                    )
                                }
                            },
                            enabled = !syncState.isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Сохранить в облако", color = Color.White)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    syncManager.restoreFromDrive(dao = dao)
                                }
                            },
                            enabled = !syncState.isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF90CAF9))
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Восстановить из облака")
                        }

                        TextButton(
                            onClick = { syncManager.signOut() },
                            enabled = !syncState.isBusy,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Выйти из аккаунта", color = Color(0xFFE57373))
                        }
                    } else {
                        Text(
                            text = "Войдите через Google, чтобы сохранять смены и тарифы в скрытую папку приложения на Google Диске и восстанавливать их на любом устройстве.",
                            color = Color(0xFFCCCCCC),
                            fontSize = 13.sp
                        )

                        Button(
                            onClick = {
                                if (activity != null) {
                                    scope.launch {
                                        val res = syncManager.signInAndAuthorize(activity)
                                        if (res.isSuccess && allEntries.isEmpty() && savedTemplates.isEmpty()) {
                                            syncManager.restoreFromDrive(dao = dao, silentIfMissing = true)
                                        }
                                    }
                                }
                            },
                            enabled = !syncState.isBusy && activity != null,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("Войти через Google", color = Color.White)
                        }
                    }

                    if (syncState.isBusy) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Выполняется синхронизация...", color = Color.White, fontSize = 13.sp)
                        }
                    }

                    syncState.statusMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = Color(0xFFFFD54F),
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCloudDialog = false }) {
                    Text("Закрыть", color = Color.White)
                }
            }
        )
    }

    selectedDayForEdit?.let { date ->
        DayEditorDialog(
            date = date,
            existingEntries = entriesByDay[date].orEmpty(),
            savedTemplates = savedTemplates,
            onDismiss = { selectedDayForEdit = null },
            onSaveTemplateOnly = { rows ->
                scope.launch {
                    val templates = rows.mapIndexed { index, row ->
                        TariffTemplate(
                            sortOrder = index,
                            tariffName = row.name.ifBlank { if (index == 0) "ОСНОВНОЙ ТАРИФ" else "ТАРИФ ${index + 1}" },
                            rateKopecks = parseRateToKopecks(row.rateText)
                        )
                    }
                    dao.clearTariffTemplates()
                    dao.insertTariffTemplates(templates)
                }
            },
            onSaveDay = { updatedRows, saveAsTemplate ->
                scope.launch {
                    if (saveAsTemplate) {
                        val templates = updatedRows.mapIndexed { index, row ->
                            TariffTemplate(
                                sortOrder = index,
                                tariffName = row.name.ifBlank { if (index == 0) "ОСНОВНОЙ ТАРИФ" else "ТАРИФ ${index + 1}" },
                                rateKopecks = parseRateToKopecks(row.rateText)
                            )
                        }
                        dao.clearTariffTemplates()
                        dao.insertTariffTemplates(templates)
                    }dao.deleteByDay(date.toEpochDay())
                    val newEntries = updatedRows.mapNotNull { row ->
                        val h = row.hoursText.toIntOrNull() ?: 0
                        val m = row.minutesText.toIntOrNull() ?: 0
                        val totalMins = h * 60 + m
                        val rateKopecks = parseRateToKopecks(row.rateText)
                        if (totalMins > 0) {
                            WorkEntry(
                                dateEpochDay = date.toEpochDay(),
                                tariffName = row.name,
                                minutes = totalMins,
                                rateKopecks = rateKopecks,
                                note = row.note
                            )
                        } else null
                    }
                    if (newEntries.isNotEmpty()) {
                        dao.insertAll(newEntries)
                    }
                    selectedDayForEdit = null
                }
            },
            onAddNightShift = { startTime, durationMinutes, tariffName, rateKopecks ->
                scope.launch {
                    val splitEntries = splitNightShift(
                        startDate = date,
                        startTime = startTime,
                        totalDurationMinutes = durationMinutes,
                        tariffName = tariffName,
                        rateKopecks = rateKopecks
                    )
                    dao.insertAll(splitEntries)
                    selectedDayForEdit = null
                }
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFFE0E0E0), fontSize = 15.sp)
        Text(
            text = value,
            color = if (highlight) Color(0xFF81C784) else Color.White,
            fontSize = 15.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun DayEditorDialog(
    date: LocalDate,
    existingEntries: List<WorkEntry>,
    savedTemplates: List<TariffTemplate>,
    onDismiss: () -> Unit,
    onSaveTemplateOnly: (List<EditableTariffRow>) -> Unit,
    onSaveDay: (List<EditableTariffRow>, Boolean) -> Unit,
    onAddNightShift: (LocalTime, Int, String, Long) -> Unit
) {
    var showNightShiftDialog by remember { mutableStateOf(false) }
    var templateSavedBanner by remember { mutableStateOf(false) }

    val rows = remember {
        mutableStateListOf<EditableTariffRow>().apply {
            if (existingEntries.isNotEmpty()) {
                existingEntries.forEach { e ->
                    add(
                        EditableTariffRow(
                            id = e.id,
                            name = e.tariffName,
                            hoursText = (e.minutes / 60).toString(),
                            minutesText = String.format(Locale.US, "%02d", e.minutes % 60),
                            rateText = formatKopecksInput(e.rateKopecks),
                            note = e.note
                        )
                    )
                }
            } else if (savedTemplates.isNotEmpty()) {
                savedTemplates.forEach { tpl ->
                    add(
                        EditableTariffRow(
                            name = tpl.tariffName,
                            hoursText = "0",
                            minutesText = "00",
                            rateText = formatKopecksInput(tpl.rateKopecks)
                        )
                    )
                }
            } else {
                add(
                    EditableTariffRow(
                        name = "ОСНОВНОЙ ТАРИФ",
                        hoursText = "0",
                        minutesText = "00",
                        rateText = "530"
                    )
                )
            }
        }
    }

    val defaultNightRate = savedTemplates.firstOrNull()?.let { formatKopecksInput(it.rateKopecks) }
        ?: rows.firstOrNull()?.rateText
        ?: "530"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF262626),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { showNightShiftDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF90CAF9))
                ) {
                    Icon(Icons.Default.NightsStay, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ночная смена")
                }

                Spacer(Modifier.height(10.dp))

                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedTextField(
                            value = row.name,
                            onValueChange = { rows[index] = row.copy(name = it) },
                            label = { Text("Название тарифа", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (rows.size > 1) {
                            IconButton(
                                onClick = { rows.removeAt(index) },
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color(0xFFE57373))
                            }
                        }
                    }

                    if (row.note.isNotEmpty()) {
                        Text(
                            text = "Примечание: ${row.note}",
                            color = Color(0xFF90CAF9),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = row.hoursText,
                            onValueChange = { rows[index] = row.copy(hoursText = it.filter(Char::isDigit).take(2)) },
                            label = { Text("Часы", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = row.minutesText,onValueChange = { rows[index] = row.copy(minutesText = it.filter(Char::isDigit).take(2)) },
                            label = { Text("Мин", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = row.rateText,
                            onValueChange = { rows[index] = row.copy(rateText = it) },
                            label = { Text("Ставка ₽", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.4f),
                            singleLine = true
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                TextButton(
                    onClick = {
                        val nextNum = rows.size + 1
                        val baseRate = rows.firstOrNull()?.rateText ?: "530"
                        rows.add(
                            EditableTariffRow(
                                name = "ТАРИФ $nextNum",
                                hoursText = "0",
                                minutesText = "00",
                                rateText = baseRate
                            )
                        )
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Добавить тариф", color = Color.White, fontSize = 15.sp)
                }

                OutlinedButton(
                    onClick = {
                        onSaveTemplateOnly(rows.toList())
                        templateSavedBanner = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF81C784))
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (templateSavedBanner) "Тарифы сохранены для всех дней ✓" else "Запомнить тарифы для других дней",
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { onSaveDay(rows.toList(), true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB73A3A)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("ОК", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showNightShiftDialog) {
        NightShiftDialog(
            date = date,
            initialRateText = defaultNightRate,
            onDismiss = { showNightShiftDialog = false },
            onConfirm = { startTime, durationMins, rateKopecks ->
                showNightShiftDialog = false
                onAddNightShift(startTime, durationMins, "НОЧНАЯ СМЕНА", rateKopecks)
            }
        )
    }
}

@Composable
fun NightShiftDialog(
    date: LocalDate,
    initialRateText: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime, Int, Long) -> Unit
) {
    var startHour by remember { mutableStateOf("00") }
    var startMin by remember { mutableStateOf("15") }
    var endHour by remember { mutableStateOf("08") }
    var endMin by remember { mutableStateOf("00") }
    var rateText by remember { mutableStateOf(initialRateText) }

    val sh = (startHour.toIntOrNull() ?: 0).coerceIn(0, 23)
    val sm = (startMin.toIntOrNull() ?: 0).coerceIn(0, 59)
    val eh = (endHour.toIntOrNull() ?: 8).coerceIn(0, 23)
    val em = (endMin.toIntOrNull() ?: 0).coerceIn(0, 59)

    val startMinuteOfDay = sh * 60 + sm
    val endMinuteOfDay = eh * 60 + em

    val totalDur = if (endMinuteOfDay > startMinuteOfDay) {
        endMinuteOfDay - startMinuteOfDay
    } else {
        (24 * 60 - startMinuteOfDay) + endMinuteOfDay
    }

    val untilMidnight = 24 * 60 - startMinuteOfDay
    val todayMins = minOf(totalDur, untilMidnight)
    val nextDayMins = (totalDur - todayMins).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2B2B2B),
        title = {
            Text(
                text = "Ночная смена (${date.format(DateTimeFormatter.ofPattern("dd.MM"))})",
                color = Color.White,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Начало смены (ЧЧ : ММ):", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startHour,
                        onValueChange = { startHour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Час") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = startMin,
                        onValueChange = { startMin = it.filter(Char::isDigit).take(2) },
                        label = { Text("Мин") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Text("Окончание смены (ЧЧ : ММ):", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = endHour,
                        onValueChange = { endHour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Час") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endMin,
                        onValueChange = { endMin = it.filter(Char::isDigit).take(2) },
                        label = { Text("Мин") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Ставка за час (₽)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Surface(
                    color = Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Всего: ${formatMinutes(totalDur)}",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "• ${date.format(DateTimeFormatter.ofPattern("dd.MM"))}: ${formatMinutes(todayMins)}",
                            color = Color(0xFF81C784),
                            fontSize = 13.sp
                        )
                        if (nextDayMins > 0) {
                            Text(
                                text = "• ${date.plusDays(1).format(DateTimeFormatter.ofPattern("dd.MM"))} (после 00:00): ${formatMinutes(nextDayMins)}",
                                color = Color(0xFF64B5F6),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(LocalTime.of(sh, sm), totalDur, parseRateToKopecks(rateText))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Добавить смену", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color(0xFFB0B0B0))
            }
        }
    )
}

private fun buildCalendarDays(yearMonth: YearMonth): List<LocalDate> {
    val firstOfMonth = yearMonth.atDay(1)
    val shift = (firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val start = firstOfMonth.minusDays(shift.toLong())
    return (0 until 42).map { start.plusDays(it.toLong()) }
}
