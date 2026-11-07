// path: app/src/main/kotlin/com/example/smsexpensertracker/ui/screens/ReportsScreen.kt
package com.example.smsexpensertracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.smsexpensertracker.R
import com.example.smsexpensertracker.ui.viewmodel.CategoryReportSlice
import com.example.smsexpensertracker.ui.viewmodel.MonthlyTrendPoint
import com.example.smsexpensertracker.ui.viewmodel.ReportPeriod
import com.example.smsexpensertracker.ui.viewmodel.ReportsUiState
import com.example.smsexpensertracker.ui.viewmodel.ReportsViewModel

// Harmonious WCAG-compliant color palette for category slices in both dark & light modes
private val SliceColors = listOf(
    Color(0xFF2E7D32), // Emerald Green (Kenyan M-Pesa theme)
    Color(0xFF0288D1), // Cyan Blue
    Color(0xFFF57C00), // Vibrant Amber
    Color(0xFF7B1FA2), // Deep Purple
    Color(0xFFD32F2F), // Crimson
    Color(0xFF00796B), // Teal
    Color(0xFFC2185B), // Pink
    Color(0xFF5D4037)  // Brown
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    navController: NavController,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = "Loading expense reports"
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Period Selector Chips with 48dp minimum touch targets
                item {
                    PeriodSelector(
                        selectedPeriod = state.selectedPeriod,
                        onPeriodSelected = { viewModel.onPeriodSelected(it) }
                    )
                }

                // KPI Overview Cards
                item {
                    KpiRow(state = state)
                }

                // Monthly Spending Trend (Compose Canvas)
                item {
                    TrendCard(trends = state.monthlyTrends)
                }

                // Category Spending Breakdown (Compose Canvas Donut)
                item {
                    CategoryBreakdownCard(
                        slices = state.categorySlices,
                        totalPeriodExpense = state.formattedTotalExpense
                    )
                }

                // Category Breakdown Legend Items
                if (state.categorySlices.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.category_details),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .semantics { heading() }
                        )
                    }
                    items(state.categorySlices) { slice ->
                        val colorIndex = (state.categorySlices.indexOf(slice)) % SliceColors.size
                        CategoryDetailRow(slice = slice, color = SliceColors[colorIndex])
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    selectedPeriod: ReportPeriod,
    onPeriodSelected: (ReportPeriod) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReportPeriod.entries.forEach { period ->
            FilterChip(
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
                label = { Text(period.label) },
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            )
        }
    }
}

@Composable
private fun KpiRow(state: ReportsUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Total Spending: ${state.formattedTotalExpense}"
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.total_spending), style = MaterialTheme.typography.labelSmall)
                Text(
                    state.formattedTotalExpense,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Monthly Average Spending: ${state.formattedAverageExpense}"
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.monthly_average), style = MaterialTheme.typography.labelSmall)
                Text(
                    state.formattedAverageExpense,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun TrendCard(trends: List<MonthlyTrendPoint>) {
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val maxPoint = trends.maxByOrNull { it.expenseKsh }
    val maxSummary = if (maxPoint != null && maxPoint.expenseKsh > 0L) {
        "Highest spending month: ${maxPoint.label} with ${maxPoint.formattedExpense}"
    } else {
        "No spending recorded across these months"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.spending_trend),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(16.dp))

            val maxExpense = (trends.maxOfOrNull { it.expenseKsh } ?: 0L).coerceAtLeast(1L)

            // Canvas Bar Chart with accessible description
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .semantics {
                        contentDescription = "Monthly spending trend bar chart across ${trends.size} months. $maxSummary."
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val barCount = trends.size
                if (barCount == 0) return@Canvas

                val slotWidth = canvasWidth / barCount
                val barWidth = slotWidth * 0.5f

                // Draw horizontal baseline
                drawLine(
                    color = gridColor,
                    start = Offset(0f, canvasHeight),
                    end = Offset(canvasWidth, canvasHeight),
                    strokeWidth = 2f
                )

                trends.forEachIndexed { index, point ->
                    val ratio = point.expenseKsh.toFloat() / maxExpense.toFloat()
                    val barHeight = (canvasHeight - 20f) * ratio
                    val left = index * slotWidth + (slotWidth - barWidth) / 2f
                    val top = canvasHeight - barHeight

                    if (barHeight > 0f) {
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                        )
                    }
                }
            }

            // Labels below bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                trends.forEach { point ->
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(
    slices: List<CategoryReportSlice>,
    totalPeriodExpense: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.spending_by_category),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() }
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (slices.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_expenses_recorded),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                val topSlice = slices.firstOrNull()
                val donutAccessibilityDescription = "Spending by category donut chart. Total spent: $totalPeriodExpense across ${slices.size} categories. Top category: ${topSlice?.categoryName ?: "none"} at ${topSlice?.percentageInt ?: 0} percent."

                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = donutAccessibilityDescription
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Donut Chart Canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        var startAngle = -90f
                        val strokeWidth = 32.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                        val arcSize = Size(diameter, diameter)

                        slices.forEachIndexed { index, slice ->
                            val sweepAngle = slice.percentage * 360f
                            val color = SliceColors[index % SliceColors.size]

                            if (sweepAngle > 0f) {
                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                )
                                startAngle += sweepAngle
                            }
                        }
                    }

                    // Donut Center Content
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = totalPeriodExpense,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDetailRow(slice: CategoryReportSlice, color: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${slice.categoryName}: ${slice.formattedExpense}, ${slice.percentageInt} percent of spending"
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = slice.categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${slice.percentageInt}% of spending",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = slice.formattedExpense,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
