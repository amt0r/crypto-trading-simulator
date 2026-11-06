package com.example.cryptotester.ui.simulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.cryptotester.domain.model.SimulationResult
import com.example.cryptotester.ui.theme.CryptoGreen
import com.example.cryptotester.ui.theme.CryptoRed
import com.example.cryptotester.ui.theme.DarkBackground
import com.example.cryptotester.ui.theme.DarkCard
import com.example.cryptotester.ui.theme.DarkSurfaceVariant
import com.example.cryptotester.ui.theme.TextPrimary
import com.example.cryptotester.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ResultDialog(
    result: SimulationResult,
    onDismiss: () -> Unit
) {
    val isProfit = result.pnlPercent >= 0
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    val primaryColor = MaterialTheme.colorScheme.primary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkCard)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isProfit) "🚀" else "📉",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.size(48.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Simulation Complete",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // PnL percentage
            Text(
                text = "%s%.2f%%".format(if (isProfit) "+" else "", result.pnlPercent),
                style = MaterialTheme.typography.headlineLarge,
                color = if (isProfit) CryptoGreen else CryptoRed,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Stats rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatRow("Start Date", dateFormat.format(Date(result.startDate)))
                StatRow("End Date", dateFormat.format(Date(result.endDate)))
                StatRow("Days Traded", "${result.daysElapsed}")
                StatRow("Starting Balance", "%.2f USDT".format(result.startBalance))
                StatRow(
                    "Final Balance",
                    "%.2f USDT".format(result.finalBalance),
                    valueColor = if (isProfit) CryptoGreen else CryptoRed
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = DarkBackground
                )
            ) {
                Text(
                    text = "Back to Home",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = TextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}
