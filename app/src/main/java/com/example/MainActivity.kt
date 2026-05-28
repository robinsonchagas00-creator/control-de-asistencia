package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.AttendanceEntry
import com.example.ui.TrackerViewModel
import com.example.ui.theme.MyApplicationTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

// Global visual helpers for currency and colors in Spanish
fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    format.maximumFractionDigits = 0
    return format.format(amount).replace("$", "$ ")
}

fun getCurrentDateString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Calendar.getInstance().time)
}

fun getCurrentTimeString(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Calendar.getInstance().time)
}

fun getFriendlyDateDisplay(dateStr: String): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val parsedDate = try { sdf.parse(dateStr) } catch (e: Exception) { null }
    return if (parsedDate != null) {
        val today = Calendar.getInstance()
        val targetCal = Calendar.getInstance().apply { time = parsedDate }
        if (today.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)) {
            "Hoy"
        } else if (today.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) - 1 == targetCal.get(Calendar.DAY_OF_YEAR)) {
            "Ayer"
        } else {
            val outFormat = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "ES"))
            outFormat.format(parsedDate).replaceFirstChar { it.uppercase() }
        }
    } else {
        dateStr
    }
}

// Convert dates to a polished localized display format
fun getFriendlyDateTimeString(dateStr: String, timeStr: String): String {
    val formattedDate = getFriendlyDateDisplay(dateStr)
    val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    val parsedTime = try { timeSdf.parse(timeStr) } catch (e: Exception) { null }
    val formattedTime = if (parsedTime != null) {
        val outTimeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        outTimeFormat.format(parsedTime).uppercase()
    } else {
        timeStr
    }

    return "$formattedDate • $formattedTime"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainAppScreen(viewModel: TrackerViewModel = viewModel()) {
    val isInitialized by viewModel.isInitialized.collectAsStateWithLifecycle()
    val fullName by viewModel.fullName.collectAsStateWithLifecycle()
    val baseSalary by viewModel.baseSalary.collectAsStateWithLifecycle()
    val qualifiesForBonus by viewModel.qualifiesForBonus.collectAsStateWithLifecycle()
    val bonusAmount by viewModel.bonusAmount.collectAsStateWithLifecycle()
    val idealSchedule by viewModel.idealSchedule.collectAsStateWithLifecycle()
    val adelanto by viewModel.adelanto.collectAsStateWithLifecycle()
    val scheduleImageUri by viewModel.scheduleImageUri.collectAsStateWithLifecycle()
    val logs by viewModel.allEntries.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var showReceiptDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    // Initials helper
    val initials = remember(fullName) {
        if (fullName.isBlank()) "RC" else {
            val parts = fullName.trim().split("\\s+".toRegex())
            if (parts.size >= 2) {
                "${parts[0].firstOrNull() ?: ""}${parts[1].firstOrNull() ?: ""}".uppercase()
            } else if (parts.isNotEmpty()) {
                parts[0].take(2).uppercase()
            } else {
                "RC"
            }
        }
    }

    // Month text descriptor
    val currentPeriodText = remember {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale("es", "ES"))
        sdf.format(Calendar.getInstance().time).replaceFirstChar { it.uppercase() }
    }

    // Calculations & Penalties
    val totalLateness = remember(logs) {
        logs.sumOf { it.latenessMinutes }
    }

    val absences = remember(logs) {
        logs.filter { it.type == "Falta" }
    }

    val absencesBefore15 = remember(absences) {
        absences.filter { viewModel.getDayFromDate(it.date) < 15 }
    }

    val absencesOnAfter15 = remember(absences) {
        absences.filter { viewModel.getDayFromDate(it.date) >= 15 }
    }

    val hasBothPeriodAbsences = remember(absencesBefore15, absencesOnAfter15) {
        absencesBefore15.isNotEmpty() && absencesOnAfter15.isNotEmpty()
    }

    val isLatenessPenaltyActive = qualifiesForBonus && (totalLateness >= 15)

    // Calculate actual attendance bonus based on Uruguayan penalty rules
    val finalBonus = remember(qualifiesForBonus, bonusAmount, totalLateness, hasBothPeriodAbsences) {
        if (!qualifiesForBonus) {
            0.0
        } else if (hasBothPeriodAbsences) {
            0.0 // Completely eliminated ($0)
        } else if (totalLateness >= 15) {
            bonusAmount * 0.5 // Reduced by 50%
        } else {
            bonusAmount // Full bonus
        }
    }

    // Monthly Earnings & Deductions
    val totalNominal = baseSalary + finalBonus
    val bpsDeduction = totalNominal * 0.22  // BPS (22% deduction as requested)
    val liquidSalaryBeforeAdvance = totalNominal - bpsDeduction
    val finalNetLiquid = liquidSalaryBeforeAdvance - adelanto // subtract cash advances

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isInitialized) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.CheckCircle, contentDescription = "Marcación") },
                        label = { Text("Marcación") },
                        modifier = Modifier.testTag("tab_marcacion")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = "Historial") },
                        label = { Text("Asistencia") },
                        modifier = Modifier.testTag("tab_historial")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Star, contentDescription = "Horarios") },
                        label = { Text("Horarios") },
                        modifier = Modifier.testTag("tab_horario")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Configuración") },
                        label = { Text("Configuración") },
                        modifier = Modifier.testTag("tab_configuracion")
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (!isInitialized) {
                // Polish Onboarding Setup
                OnboardingView(
                    onSubmit = { name: String, salary: Double, qualifiers: Boolean, bonus: Double ->
                        viewModel.updateProfile(name, salary, qualifiers, bonus)
                    }
                )
            } else {
                // Render selected tab
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "TabTransition"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> MarcacionTab(
                            logs = logs,
                            viewModel = viewModel,
                            onAddLog = { type, date, time, lateness, notes ->
                                viewModel.addLog(date, type, time, lateness, notes)
                            },
                            onDeleteLog = { id -> viewModel.deleteLog(id) }
                        )
                        1 -> HistorialTab(
                            logs = logs,
                            onDeleteLog = { id -> viewModel.deleteLog(id) },
                            onClearAll = { viewModel.clearAllLogs() }
                        )
                        2 -> HorarioImageTab(
                            scheduleImageUri = scheduleImageUri,
                            idealSchedule = idealSchedule,
                            onUpdateImage = { uri -> viewModel.updateScheduleImageUri(uri) },
                            onSaveScheduleText = { text -> viewModel.updateIdealSchedule(text) }
                        )
                        3 -> ConfiguracionTab(
                            fullName = fullName,
                            baseSalary = baseSalary,
                            qualifiesForBonus = qualifiesForBonus,
                            bonusAmount = bonusAmount,
                            adelanto = adelanto,
                            totalNominal = totalNominal,
                            bpsDeduction = bpsDeduction,
                            finalNetLiquid = finalNetLiquid,
                            totalLateness = totalLateness,
                            absencesCount = absences.size,
                            absencesBefore15 = absencesBefore15.size,
                            absencesAfter15 = absencesOnAfter15.size,
                            hasBothPeriodAbsences = hasBothPeriodAbsences,
                            isLatenessPenaltyActive = isLatenessPenaltyActive,
                            onUpdateAdelanto = { amount -> viewModel.updateAdelanto(amount) },
                            onUpdateProfile = { name, salary, qual, bAmt ->
                                viewModel.updateProfile(name, salary, qual, bAmt)
                            },
                            onEmitReceipt = { showReceiptDialog = true },
                            onReset = { viewModel.resetApp() }
                        )
                    }
                }
            }
        }
    }

    // Edit Profile Dynamic Dialog
    if (showEditProfileDialog) {
        EditProfileDialog(
            currentName = fullName,
            currentSalary = baseSalary,
            currentQualifies = qualifiesForBonus,
            currentBonus = bonusAmount,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, salary, qualifiers, bonus ->
                viewModel.updateProfile(name, salary, qualifiers, bonus)
                showEditProfileDialog = false
            }
        )
    }

    // Receipt / Recibo payslip popup dialogue
    if (showReceiptDialog) {
        ReceiptPayslipDialog(
            fullName = fullName,
            baseSalary = baseSalary,
            finalBonus = finalBonus,
            bpsDeduction = bpsDeduction,
            adelanto = adelanto,
            finalNetLiquid = finalNetLiquid,
            totalLateness = totalLateness,
            absencesCount = absences.size,
            hasBothPeriodAbsences = hasBothPeriodAbsences,
            qualifiesForBonus = qualifiesForBonus,
            bonusAmount = bonusAmount,
            periodText = currentPeriodText,
            onDismiss = { showReceiptDialog = false }
        )
    }
}

// ==================== TAB 0: MARCACIÓN (CLOCKING ACTIONS) ====================
@Composable
fun MarcacionTab(
    logs: List<AttendanceEntry>,
    viewModel: TrackerViewModel,
    onAddLog: (String, String, String, Int, String) -> Unit,
    onDeleteLog: (Int) -> Unit
) {
    val todayStr = remember { getCurrentDateString() }
    val todayLogs = remember(logs) {
        logs.filter { it.date == todayStr }
    }

    // State machine logic for single button clocking
    val currentCycleState = remember(todayLogs) {
        val hasIn = todayLogs.any { it.type == "In" }
        val hasBreakStart = todayLogs.any { it.type == "Break Start" }
        val hasBreakEnd = todayLogs.any { it.type == "Break End" }
        val hasOut = todayLogs.any { it.type == "Out" }
        val hasFalta = todayLogs.any { it.type == "Falta" }

        when {
            hasFalta -> "Falta"
            !hasIn -> "In"
            !hasBreakStart -> "Break Start"
            !hasBreakEnd -> "Break End"
            !hasOut -> "Out"
            else -> "Completo"
        }
    }

    var latenessMinutes by remember { mutableStateOf("0") }
    var notesText by remember { mutableStateOf("") }
    var showManualLogDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E2EC))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Marcación de Hoy:",
                        fontSize = 13.sp,
                        color = Color(0xFF49454F)
                    )
                    Text(
                        text = getFriendlyDateDisplay(todayStr),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F)
                    )
                }
            }
        }

        // Single button interface
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E2EC))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Marcación Unificada",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF49454F)
                )

                // The prominent button
                val buttonColor = when (currentCycleState) {
                    "In" -> Color(0xFF4CAF50) // Green
                    "Break Start" -> Color(0xFFFF9800) // Amber
                    "Break End" -> Color(0xFF2196F3) // Blue
                    "Out" -> Color(0xFFE91E63) // Pink/Salmon
                    "Falta" -> Color(0xFF757575) // Gray
                    else -> Color(0xFF9C27B0) // Purple (Complete)
                }

                val buttonLabel = when (currentCycleState) {
                    "In" -> "Registrar Entrada"
                    "Break Start" -> "Entrada a la Media Hora"
                    "Break End" -> "Salida de la Media Hora"
                    "Out" -> "Registrar Salida de Trabajo"
                    "Falta" -> "Inasistencia Hoy"
                    else -> "Jornada Completa 🎉"
                }

                val buttonDesc = when (currentCycleState) {
                    "In" -> "Toca para registrar el ingreso a tu jornada de trabajo."
                    "Break Start" -> "Toca para registrar el inicio de tu descanso de 30 mins."
                    "Break End" -> "Toca al finalizar tus 30 minutos de descanso para retornar."
                    "Out" -> "Toca para marcar el egreso / salida final del trabajo."
                    "Falta" -> "Se ha guardado un evento de inasistencia para el día de hoy."
                    else -> "¡Excelente! Has registrado todos los tramos laborables del día."
                }

                val buttonIcon = when (currentCycleState) {
                    "In" -> Icons.Default.PlayArrow
                    "Break Start" -> Icons.Default.Lock
                    "Break End" -> Icons.Default.PlayArrow
                    "Out" -> Icons.Default.Close
                    "Falta" -> Icons.Default.Warning
                    else -> Icons.Default.CheckCircle
                }

                Box(
                    modifier = Modifier
                        .size(174.dp)
                        .clip(RoundedCornerShape(87.dp))
                        .background(buttonColor.copy(alpha = 0.12f))
                        .clickable(enabled = currentCycleState != "Completo" && currentCycleState != "Falta") {
                            val timeStr = getCurrentTimeString()
                            val lateInt = latenessMinutes.toIntOrNull() ?: 0
                            onAddLog(currentCycleState, todayStr, timeStr, lateInt, notesText)
                            // Reset optional inputs
                            latenessMinutes = "0"
                            notesText = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .clip(RoundedCornerShape(68.dp))
                            .background(buttonColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = buttonIcon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (currentCycleState == "Completo") "Listo" else if (currentCycleState == "In") "Entrar" else if (currentCycleState == "Out") "Salir" else "Descanso",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Description details
                Text(
                    text = buttonLabel,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = buttonColor
                )

                Text(
                    text = buttonDesc,
                    fontSize = 12.sp,
                    color = Color(0xFF49454F),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Optional panel inputs (lateness and description notes) displayed for inputs
                if (currentCycleState == "In") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = Color(0xFFE1E2EC))
                        Text(
                            text = "¿Llegaste tarde hoy? Registra los minutos de tardanza:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF49454F)
                        )
                        OutlinedTextField(
                            value = latenessMinutes,
                            onValueChange = { latenessMinutes = it },
                            label = { Text("Tardanza (Minutos)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (currentCycleState != "Completo" && currentCycleState != "Falta") {
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Notas o descripción (Opcional)") },
                        placeholder = { Text("Ej. Cambio de turno, normal, etc.") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Alternative quick action: Mark absence "Falta"
                if (currentCycleState == "In") {
                    Button(
                        onClick = {
                            onAddLog("Falta", todayStr, "00:00", 0, "Registrado por botón rápido")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hoy Falté (Inasistencia)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Option to log with custom override date
                OutlinedButton(
                    onClick = { showManualLogDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Marcar Otro Día / Manual", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Today's timeline block
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E2EC))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Movimientos de Hoy",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F)
                )

                if (todayLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No has registrado movimientos hoy aún.",
                            fontSize = 12.sp,
                            color = Color(0xFF49454F).copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    todayLogs.forEach { log ->
                        val entryLabel = when (log.type) {
                            "In" -> "Entrada al Trabajo"
                            "Break Start" -> "Inicio Descanso"
                            "Break End" -> "Fin Descanso"
                            "Out" -> "Salida del Trabajo"
                            "Falta" -> "Inasistencia Grabada"
                            else -> log.type
                        }

                        val colorIndicator = when (log.type) {
                            "In" -> Color(0xFF4CAF50)
                            "Break Start" -> Color(0xFFFF9800)
                            "Break End" -> Color(0xFF2196F3)
                            "Out" -> Color(0xFFE91E63)
                            else -> Color(0xFFF44336)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF4F3F7), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colorIndicator)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = entryLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1C1B1F)
                                    )
                                    Text(
                                        text = "Hora: ${log.time} hs" + (if (log.latenessMinutes > 0) " (${log.latenessMinutes}m tarde)" else ""),
                                        fontSize = 11.sp,
                                        color = Color(0xFF49454F)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onDeleteLog(log.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Eliminar",
                                    tint = Color(0xFFBA1A1A),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Creado y desarrollado por Robinson Chagas",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }

    // Manual Clocking Modal Dialogue override
    if (showManualLogDialog) {
        ManualLogDialog(
            onDismiss = { showManualLogDialog = false },
            onAddLog = { type, date, time, late, notes ->
                onAddLog(type, date, time, late, notes)
                showManualLogDialog = false
            }
        )
    }
}

// ==================== TAB 1: DÍAS TRABAJADOS (WORKED DAYS HISTORY) ====================
@Composable
fun HistorialTab(
    logs: List<AttendanceEntry>,
    onDeleteLog: (Int) -> Unit,
    onClearAll: () -> Unit
) {
    // Group logs by DATE
    val groupedLogs = remember(logs) {
        logs.groupBy { it.date }.toList().sortedByDescending { it.first }
    }

    var showConfirmDeleteAll by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Días de Trabajo Registrados (${groupedLogs.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1B1F)
            )

            if (logs.isNotEmpty()) {
                TextButton(
                    onClick = { showConfirmDeleteAll = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFBA1A1A))
                ) {
                    Text("Limpiar Todo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        if (groupedLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF49454F).copy(alpha = 0.4f),
                        modifier = Modifier.size(52.dp)
                    )
                    Text(
                        text = "No hay registros grabados aún.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF49454F).copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Utiliza el botón central en la pestaña de Marcación.",
                        fontSize = 11.sp,
                        color = Color(0xFF49454F).copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Scrollable column with a simple forEach to avoid any compiler/imports bugs with items LazyColumn
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                groupedLogs.forEach { (date, entries) ->
                    val sortedEntries = entries.sortedBy { it.time }
                    val isAbsence = entries.any { it.type == "Falta" }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE1E2EC))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Date row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = getFriendlyDateDisplay(date),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                if (isAbsence) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFFFDAD6))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Falta",
                                            color = Color(0xFF410002),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFE8DEF8))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${entries.size} mov",
                                            color = Color(0xFF1D192B),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE1E2EC).copy(alpha = 0.6f))

                            // Individual clocks list
                            sortedEntries.forEach { entry ->
                                val entryLabel = when (entry.type) {
                                    "In" -> "Entrada"
                                    "Break Start" -> "Inicio Descanso"
                                    "Break End" -> "Fin Descanso"
                                    "Out" -> "Salida Trabajo"
                                    "Falta" -> "Inasistencia Grabada"
                                    else -> entry.type
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (entry.type == "Falta") Icons.Default.Warning else Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = if (entry.type == "Falta") Color(0xFFBA1A1A) else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "$entryLabel: ",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF1C1B1F)
                                        )
                                        Text(
                                            text = entry.time + " hs",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1C1B1F)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (entry.latenessMinutes > 0) {
                                            Text(
                                                text = "+${entry.latenessMinutes}m tarde",
                                                color = Color(0xFFBA1A1A),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { onDeleteLog(entry.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Eliminar Registro",
                                                tint = Color(0xFFBA1A1A).copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                                if (entry.notes.isNotBlank()) {
                                    Text(
                                        text = "↳ Nota: ${entry.notes}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF49454F).copy(alpha = 0.8f),
                                        modifier = Modifier.padding(start = 22.dp, bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Creado y desarrollado por Robinson Chagas",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showConfirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteAll = false },
            title = { Text("¿Deseas limpiar todos los registros?", fontWeight = FontWeight.Bold) },
            text = { Text("Esta acción eliminará de forma irreversible todo el historial cargado del mes.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showConfirmDeleteAll = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Borrar Todo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteAll = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// ==================== TAB 2: HORARIO DE LA SEMANA (IMAGE TAB) ====================
@Composable
fun HorarioImageTab(
    scheduleImageUri: String,
    idealSchedule: String,
    onUpdateImage: (String) -> Unit,
    onSaveScheduleText: (String) -> Unit
) {
    val context = LocalContext.current
    var isEditingText by remember { mutableStateOf(false) }
    var scheduleTextVal by remember { mutableStateOf(idealSchedule) }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                // Safe if persistable permission fails
            }
            onUpdateImage(it.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Welcoming explanation
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E2EC))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Imagen de Horarios Semanales",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1C1B1F)
                    )
                }
                Text(
                    text = "Carga aquí una captura de pantalla, foto o planilla de tus horarios semanales para tenerla siempre disponible.",
                    fontSize = 12.sp,
                    color = Color(0xFF49454F)
                )
            }
        }

        // Image display area
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E2EC))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (scheduleImageUri.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF4F3F7))
                    ) {
                        AsyncImage(
                            model = scheduleImageUri,
                            contentDescription = "Calendario Semanal",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.Center
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { imageLauncher.launch("image/*") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cambiar Imagen")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onUpdateImage("") },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBA1A1A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Eliminar")
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF49454F).copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No has cargado ninguna imagen.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF49454F)
                        )
                        Button(
                            onClick = { imageLauncher.launch("image/*") },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Seleccionar Imagen de Calendario/Horarios")
                        }
                    }
                }
            }
        }

        // Ideal Schedule text helper
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E2EC))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Turno Semanal Ideal (Fijado)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1C1B1F)
                    )
                    TextButton(
                        onClick = {
                            if (isEditingText) {
                                onSaveScheduleText(scheduleTextVal)
                            }
                            isEditingText = !isEditingText
                        }
                    ) {
                        Text(if (isEditingText) "Guardar" else "Editar")
                    }
                }

                if (isEditingText) {
                    OutlinedTextField(
                        value = scheduleTextVal,
                        onValueChange = { scheduleTextVal = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 4
                    )
                } else {
                    Text(
                        text = idealSchedule.ifBlank { "Lunes a Viernes de 09:00 a 18:00 hs (Modificable)" },
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF49454F),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF4F3F7), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Creado y desarrollado por Robinson Chagas",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}

// ==================== TAB 3: CONFIGURACIÓN Y CALCULOS ====================
@Composable
fun ConfiguracionTab(
    fullName: String,
    baseSalary: Double,
    qualifiesForBonus: Boolean,
    bonusAmount: Double,
    adelanto: Double,
    totalNominal: Double,
    bpsDeduction: Double,
    finalNetLiquid: Double,
    totalLateness: Int,
    absencesCount: Int,
    absencesBefore15: Int,
    absencesAfter15: Int,
    hasBothPeriodAbsences: Boolean,
    isLatenessPenaltyActive: Boolean,
    onUpdateAdelanto: (Double) -> Unit,
    onUpdateProfile: (String, Double, Boolean, Double) -> Unit,
    onEmitReceipt: () -> Unit,
    onReset: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showConfirmReset by remember { mutableStateOf(false) }

    var adelantoInput by remember { mutableStateOf(if (adelanto == 0.0) "" else adelanto.toInt().toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E2EC))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Información Laboral",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1C1B1F)
                    )
                    OutlinedButton(
                        onClick = { showEditDialog = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Modificar", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = Color(0xFFE1E2EC).copy(alpha = 0.6f))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Empleado:", fontSize = 13.sp, color = Color(0xFF49454F))
                    Text(text = fullName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Sueldo Nominal Básico:", fontSize = 13.sp, color = Color(0xFF49454F))
                    Text(text = formatCurrency(baseSalary), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Bono de Presentismo:", fontSize = 13.sp, color = Color(0xFF49454F))
                    Text(text = if (qualifiesForBonus) formatCurrency(bonusAmount) else "No habilitado", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (qualifiesForBonus) Color(0xFF1C1B1F) else Color.Gray)
                }
            }
        }

        // Cash Advance (Adelantos) Card input
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E2EC))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Registrar Adelantos del Mes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1C1B1F)
                )
                Text(
                    text = "Si solicitaste dinero de adelanto, indícalo aquí para deducirlo automáticamente de tu sueldo líquido.",
                    fontSize = 12.sp,
                    color = Color(0xFF49454F)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = adelantoInput,
                        onValueChange = { adelantoInput = it },
                        label = { Text("Monto del Adelanto ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val amt = adelantoInput.toDoubleOrNull() ?: 0.0
                            onUpdateAdelanto(amt)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Deducir", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Calculations Display Block
        Text(
            text = "Resultado y Deducciones Mensuales",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color(0xFF49454F),
            modifier = Modifier.padding(start = 2.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "CÁLCULO ESTIMADO DE SUELDO LÍQUIDO",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    letterSpacing = 1.sp
                )

                Text(
                    text = formatCurrency(finalNetLiquid),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

                // Breakdown list
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Sueldo Nominal Base:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(text = formatCurrency(baseSalary), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val bonusLabel = if (qualifiesForBonus) {
                        if (hasBothPeriodAbsences) "Presentismo (Eliminado - faltas):"
                        else if (totalLateness >= 15) "Presentismo (Penalidad -50%):"
                        else "Presentismo Ganado:"
                    } else {
                        "Presentismo (No habilitado):"
                    }
                    val currentEarnedBonus = if (qualifiesForBonus) {
                        if (hasBothPeriodAbsences) 0.0
                        else if (totalLateness >= 15) bonusAmount * 0.5
                        else bonusAmount
                    } else {
                        0.0
                    }
                    Text(text = bonusLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(text = formatCurrency(currentEarnedBonus), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Total Nominal Acumulado:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f))
                    Text(text = formatCurrency(totalNominal), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Deducción BPS (22%):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(text = "- " + formatCurrency(bpsDeduction), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBA1A1A))
                }

                if (adelanto > 0.0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Adelanto Deducido:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        Text(text = "- " + formatCurrency(adelanto), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBA1A1A))
                    }
                }
            }
        }

        // Penalty notices
        PenaltyAlertBox(
            isLatenessPenalty = isLatenessPenaltyActive,
            isAbsencePenalty = hasBothPeriodAbsences,
            bonusAmount = bonusAmount,
            totalLateness = totalLateness,
            absencesBefore15 = absencesBefore15,
            absencesAfter15 = absencesAfter15
        )

        // Action button to EMIT RECEIPT
        Button(
            onClick = onEmitReceipt,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generar Recibo de Sueldo", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        // Dangerous reset section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFDAD6)),
            border = BorderStroke(1.dp, Color(0xFFBA1A1A).copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reiniciar Aplicación", color = Color(0xFF410002), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Elimina el perfil y los registros", color = Color(0xFF410002).copy(alpha = 0.8f), fontSize = 11.sp)
                }
                TextButton(
                    onClick = { showConfirmReset = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFBA1A1A))
                ) {
                    Text("Reiniciar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Creado y desarrollado por Robinson Chagas",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showConfirmReset) {
        AlertDialog(
            onDismissRequest = { showConfirmReset = false },
            title = { Text("¿Deseas reiniciar la aplicación?", fontWeight = FontWeight.Bold) },
            text = { Text("Se borrarán tus datos de perfil, salarios, adelantos y TODO el historial del mes.") },
            confirmButton = {
                Button(
                    onClick = {
                        onReset()
                        showConfirmReset = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reiniciar app")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmReset = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showEditDialog) {
        EditProfileDialog(
            currentName = fullName,
            currentSalary = baseSalary,
            currentQualifies = qualifiesForBonus,
            currentBonus = bonusAmount,
            onDismiss = { showEditDialog = false },
            onSave = { name, salary, qualifies, bonus ->
                onUpdateProfile(name, salary, qualifies, bonus)
                showEditDialog = false
            }
        )
    }
}

// ==================== MANUAL LOG MODAL DIALOG ====================
@Composable
fun ManualLogDialog(
    onDismiss: () -> Unit,
    onAddLog: (String, String, String, Int, String) -> Unit
) {
    val eventTypes = listOf("In", "Out", "Break Start", "Break End", "Falta")
    var selectedType by remember { mutableStateOf("In") }

    var dateStr by remember { mutableStateOf(getCurrentDateString()) }
    var timeStr by remember { mutableStateOf(getCurrentTimeString()) }
    var latenessStr by remember { mutableStateOf("0") }
    var notesStr by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marcación Manual", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Selecciona tipo de tramo:", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    eventTypes.forEach { type ->
                        val isSelected = selectedType == type
                        val displayType = when (type) {
                            "In" -> "Entrada"
                            "Out" -> "Salida"
                            "Break Start" -> "Inicio Descanso"
                            "Break End" -> "Fin Descanso"
                            "Falta" -> "Inasistencia"
                            else -> type
                        }

                        Button(
                            onClick = { selectedType = type },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE1E2EC),
                                contentColor = if (isSelected) Color.White else Color(0xFF49454F)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(displayType, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("Fecha (AAAA-MM-DD)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = timeStr,
                    onValueChange = { timeStr = it },
                    label = { Text("Hora (HH:MM)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedType == "In") {
                    OutlinedTextField(
                        value = latenessStr,
                        onValueChange = { latenessStr = it },
                        label = { Text("Minutos de Demora") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = notesStr,
                    onValueChange = { notesStr = it },
                    label = { Text("Notas / Descripción") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lateness = latenessStr.toIntOrNull() ?: 0
                    if (dateStr.split("-").size < 3) {
                        errorMessage = "Formato de fecha inválido. Ej. YYYY-MM-DD"
                    } else if (timeStr.split(":").size < 2) {
                        errorMessage = "Formato de hora inválido. Ej. HH:MM"
                    } else {
                        onAddLog(selectedType, dateStr, timeStr, lateness, notesStr)
                    }
                }
            ) {
                Text("Grabar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// ==================== RECEIPT / EMPRESARIAL PAYSLIP DIALOGUE ====================
@Composable
fun ReceiptPayslipDialog(
    fullName: String,
    baseSalary: Double,
    finalBonus: Double,
    bpsDeduction: Double,
    adelanto: Double,
    finalNetLiquid: Double,
    totalLateness: Int,
    absencesCount: Int,
    hasBothPeriodAbsences: Boolean,
    qualifiesForBonus: Boolean,
    bonusAmount: Double,
    periodText: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recibo de Haberes Mensual", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .border(1.dp, Color.LightGray, RoundedCornerShape(14.dp))
                    .background(Color(0xFFF9F9FB))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mock printed header
                Text(
                    text = "CONTROL DE ASISTENCIA PERSONAL",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    color = Color.DarkGray,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "RECEPCIÓN DOCUMENTAL DE HABERES",
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = Color.LightGray)

                // Info block
                Text(text = "Trabajador: ${fullName.uppercase()}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(text = "Período: ${periodText.uppercase()}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(text = "Localización: Montevideo, Uruguay", fontSize = 11.sp, fontFamily = FontFamily.Monospace)

                HorizontalDivider(color = Color.LightGray)

                // Ledgers
                Text(text = "HABERES / INGRESOS:", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF4CAF50))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Sueldo Nominal Base", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(text = formatCurrency(baseSalary), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }

                if (finalBonus > 0.0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Bono Presentismo (Ganado)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(text = formatCurrency(finalBonus), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                } else if (qualifiesForBonus) {
                    val failReason = if (hasBothPeriodAbsences) "Eliminado por faltas" else "No otorgado / Penalidad"
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Bono Presentismo ($failReason)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                        Text(text = "$ 0", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                    }
                }

                HorizontalDivider(color = Color.LightGray, modifier = Modifier.padding(vertical = 4.dp))

                Text(text = "DESCUENTOS / RETENCIONES:", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFFBA1A1A))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Deducción de BPS (22%)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "- " + formatCurrency(bpsDeduction), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFBA1A1A))
                }

                if (adelanto > 0.0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Adelanto mensual descontado", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "- " + formatCurrency(adelanto), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFBA1A1A))
                    }
                }

                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))

                // SUM NET LIQUID
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "LÍQUIDO NETO A COBRAR:", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(text = formatCurrency(finalNetLiquid), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                }

                HorizontalDivider(color = Color.LightGray)

                // Disclaimer stats
                Text(text = "Métricas de Control Asistencia:", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                Text(text = "- Tardanzas Totales: $totalLateness minutos (Límite: 15 min)", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                Text(text = "- Faltas Registradas: $absencesCount inasistencias", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Creado y desarrollado por Robinson Chagas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Listo")
            }
        }
    )
}

@Composable
fun PenaltyAlertBox(
    isLatenessPenalty: Boolean,
    isAbsencePenalty: Boolean,
    bonusAmount: Double,
    totalLateness: Int,
    absencesBefore15: Int,
    absencesAfter15: Int
) {
    if (isLatenessPenalty || isAbsencePenalty) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            border = BorderStroke(1.dp, Color(0xFFBA1A1A).copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Advertencia de penalidad activa",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "¡Penalidad de Presentismo Aplicada!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                if (isAbsencePenalty) {
                    Text(
                        text = "❌ Bono Eliminado ($0): Registraste inasistencias en ambos segmentos del mes (antes del 15: $absencesBefore15, a partir del 15: $absencesAfter15). Esto anula por completo tu presentismo.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                        lineHeight = 16.sp
                    )
                } else if (isLatenessPenalty) {
                    Text(
                        text = "⚠️ Bono Reducido al 50%: Tus demoras acumuladas son de $totalLateness minutos, alcanzando o superando el límite de 15 minutos. Tu presentismo se reduce a la mitad: ${formatCurrency(bonusAmount * 0.5)}.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun EditProfileDialog(
    currentName: String,
    currentSalary: Double,
    currentQualifies: Boolean,
    currentBonus: Double,
    onDismiss: () -> Unit,
    onSave: (String, Double, Boolean, Double) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var salaryStr by remember { mutableStateOf(currentSalary.toInt().toString()) }
    var qualifies by remember { mutableStateOf(currentQualifies) }
    var bonusStr by remember { mutableStateOf(currentBonus.toInt().toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Información Salarial", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1C1B1F)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre Completo") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_name_input")
                )

                OutlinedTextField(
                    value = salaryStr,
                    onValueChange = { salaryStr = it },
                    label = { Text("Sueldo Nominal Básico ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_salary_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Eligible para Presentismo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F)
                    )
                    Switch(
                        checked = qualifies,
                        onCheckedChange = { qualifies = it },
                        modifier = Modifier.testTag("edit_qualifiers_switch")
                    )
                }

                if (qualifies) {
                    OutlinedTextField(
                        value = bonusStr,
                        onValueChange = { bonusStr = it },
                        label = { Text("Monto del Bono de Presentismo ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_bonus_input")
                    )
                }

                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val salaryNum = salaryStr.toDoubleOrNull()
                    val bonusNum = if (qualifies) bonusStr.toDoubleOrNull() else 0.0

                    if (name.isBlank()) {
                        errorText = "El nombre no puede estar vacío."
                    } else if (salaryNum == null || salaryNum <= 0) {
                        errorText = "Por favor ingresa un sueldo básico válido."
                    } else if (qualifies && (bonusNum == null || bonusNum < 0)) {
                        errorText = "Por favor ingresa un monto de bono de asistencia válido."
                    } else {
                        errorText = null
                        onSave(name, salaryNum, qualifies, bonusNum ?: 0.0)
                    }
                },
                modifier = Modifier.testTag("edit_save_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Text("Guardar Cambios", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("edit_cancel_button")
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun OnboardingView(onSubmit: (String, Double, Boolean, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var salaryStr by remember { mutableStateOf("") }
    var qualifies by remember { mutableStateOf(true) }
    var bonusStr by remember { mutableStateOf("0") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            border = BorderStroke(1.dp, Color(0xFFE1E2EC))
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Greeting and brand
                Icon(
                    imageVector = Icons.Default.AccountBox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(12.dp)
                )

                Text(
                    text = "¡Bienvenido a Asistencia & Sueldo!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Para comenzar, configura tu perfil y tus condiciones salariales básicas corporativas:",
                    fontSize = 12.sp,
                    color = Color(0xFF49454F),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Name input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre Completo") },
                    placeholder = { Text("Ej. Robinson Chagas") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_name_input"),
                    leadingIcon = { Icon(Icons.Default.AccountBox, contentDescription = null) }
                )

                // Base Salary input
                OutlinedTextField(
                    value = salaryStr,
                    onValueChange = { salaryStr = it },
                    label = { Text("Sueldo Nominal Básico ($)") },
                    placeholder = { Text("Ej. 45000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_salary_input"),
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                )

                // Attendance bonus switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bono de Presentismo",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C1B1F)
                        )
                        Text(
                            text = "Habilita si cobras incentivo por asistencia.",
                            fontSize = 11.sp,
                            color = Color(0xFF49454F)
                        )
                    }
                    Switch(
                        checked = qualifies,
                        onCheckedChange = { qualifies = it },
                        modifier = Modifier.testTag("onboarding_bonus_switch")
                    )
                }

                // If qualifies, show bonus amount
                if (qualifies) {
                    OutlinedTextField(
                        value = bonusStr,
                        onValueChange = { bonusStr = it },
                        label = { Text("Monto del Bono de Presentismo ($)") },
                        placeholder = { Text("Ej. 6000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_bonus_input"),
                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                    )
                }

                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val salaryNum = salaryStr.toDoubleOrNull()
                        val bonusNum = if (qualifies) bonusStr.toDoubleOrNull() else 0.0

                        if (name.isBlank()) {
                            errorText = "Por favor, ingresa tu nombre completo."
                        } else if (salaryNum == null || salaryNum <= 0) {
                            errorText = "Ingresa un sueldo básico nominal válido."
                        } else if (qualifies && (bonusNum == null || bonusNum < 0)) {
                            errorText = "Ingresa un monto de bono de presentismo válido o deshabilítalo."
                        } else {
                            errorText = null
                            onSubmit(name.trim(), salaryNum, qualifies, bonusNum ?: 0.0)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("onboarding_submit_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Comenzar a Controlar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Creado y desarrollado por Robinson Chagas",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
