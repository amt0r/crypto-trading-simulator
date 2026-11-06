package com.example.cryptotester.ui.simulator

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.cryptotester.domain.model.CryptoSymbol
import com.example.cryptotester.domain.model.OrderType
import com.example.cryptotester.domain.model.PendingOrder
import com.example.cryptotester.domain.model.Timeframe
import com.example.cryptotester.domain.model.TradeSide
import com.example.cryptotester.ui.simulator.chart.CandlestickChart
import com.example.cryptotester.ui.simulator.chart.RsiChart
import com.example.cryptotester.ui.theme.BtcColor
import com.example.cryptotester.ui.theme.CryptoGreen
import com.example.cryptotester.ui.theme.CryptoGreenSurface
import com.example.cryptotester.ui.theme.CryptoRed
import com.example.cryptotester.ui.theme.CryptoRedSurface
import com.example.cryptotester.ui.theme.DarkBackground
import com.example.cryptotester.ui.theme.DarkCard
import com.example.cryptotester.ui.theme.DarkSurface
import com.example.cryptotester.ui.theme.DarkSurfaceVariant
import com.example.cryptotester.ui.theme.EthColor
import com.example.cryptotester.ui.theme.SolColor
import com.example.cryptotester.ui.theme.TextPrimary
import com.example.cryptotester.ui.theme.TextSecondary
import com.example.cryptotester.ui.theme.TextTertiary

@Composable
fun SimulatorScreen(
    onNavigateBack: () -> Unit,
    viewModel: SimulatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var crosshairOffset by remember { mutableStateOf<Offset?>(null) }

    // Show result dialog
    if (uiState.showResult && uiState.simulationResult != null) {
        ResultDialog(
            result = uiState.simulationResult!!,
            onDismiss = {
                viewModel.onResultDismissed()
                onNavigateBack()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Scrollable content area — tap outside the chart dismisses crosshair
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) {
                    detectTapGestures {
                        crosshairOffset = null
                    }
                }
        ) {
            // Top Bar
            TopBar(
                usdtBalance = uiState.usdtBalance,
                cryptoBalance = uiState.cryptoBalance,
                cryptoSymbol = uiState.selectedSymbol,
                obfuscatedPrice = uiState.obfuscatedPrice,
                sessionTimeFormatted = uiState.sessionTimeFormatted,
                onFinish = viewModel::onFinish
            )

            // Asset Selector
            AssetSelector(
                selected = uiState.selectedSymbol,
                onSelect = viewModel::onSymbolSelected
            )

            // Timeframe Selector
            TimeframeSelector(
                selected = uiState.selectedTimeframe,
                onSelect = viewModel::onTimeframeSelected
            )

            // Candlestick Chart
            CandlestickChart(
                candles = uiState.obfuscatedCandles,
                crosshairOffset = crosshairOffset,
                onCrosshairChange = { offset, price ->
                    crosshairOffset = offset
                    if (price != null) {
                        viewModel.onPriceSelectedFromChart(price)
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // RSI Chart
            Text(
                text = "RSI (14)",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
            RsiChart(
                rsiValues = uiState.rsiValues,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Trade notification message
            if (uiState.tradeMessage != null) {
                Text(
                    text = uiState.tradeMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Trading Controls (Market, Limit, Trailing Stop)
            TradingControls(
                selectedOrderType = uiState.selectedOrderType,
                onOrderTypeSelected = viewModel::onOrderTypeSelected,
                limitPriceInput = uiState.limitPriceInput,
                onLimitPriceChanged = viewModel::onLimitPriceChanged,
                activationPriceInput = uiState.activationPriceInput,
                onActivationPriceChanged = viewModel::onActivationPriceChanged,
                trailingPercentInput = uiState.trailingPercentInput,
                onTrailingPercentChanged = viewModel::onTrailingPercentChanged,
                sliderPosition = uiState.sliderPosition,
                onSliderChanged = viewModel::onSliderChanged,
                canBuy = uiState.canBuy,
                canSell = uiState.canSell,
                onBuy = viewModel::onBuy,
                onSell = viewModel::onSell,
                pendingOrders = uiState.pendingOrders,
                onCancelPendingOrder = viewModel::onCancelPendingOrder
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Sticky bottom — Next Candle Buttons (15 min, 1 hour, 1 day)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkBackground)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = viewModel::onAdvance15m,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = DarkBackground
                )
            ) {
                Text(
                    text = "15 min ▶",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = viewModel::onAdvance1h,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = DarkBackground
                )
            ) {
                Text(
                    text = "1 hour ▶",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = viewModel::onAdvance1d,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = DarkBackground
                )
            ) {
                Text(
                    text = "1 day ▶",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    usdtBalance: Double,
    cryptoBalance: Double,
    cryptoSymbol: CryptoSymbol,
    obfuscatedPrice: Double,
    sessionTimeFormatted: String,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "%.2f USDT".format(usdtBalance),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "%.6f %s".format(cryptoBalance, cryptoSymbol.displayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = sessionTimeFormatted,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Price: %.2f".format(obfuscatedPrice),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            TextButton(
                onClick = onFinish,
                shape = RectangleShape,
                colors = ButtonDefaults.textButtonColors(contentColor = CryptoRed)
            ) {
                Text(
                    text = "Finish",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AssetSelector(
    selected: CryptoSymbol,
    onSelect: (CryptoSymbol) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val assets = listOf(
            CryptoSymbol.BTC to BtcColor,
            CryptoSymbol.ETH to EthColor,
            CryptoSymbol.SOL to SolColor
        )

        assets.forEach { (symbol, color) ->
            val isSelected = selected == symbol
            Button(
                onClick = { onSelect(symbol) },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) color.copy(alpha = 0.2f) else DarkSurfaceVariant,
                    contentColor = if (isSelected) color else TextSecondary
                )
            ) {
                Text(
                    text = symbol.displayName,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun TimeframeSelector(
    selected: Timeframe,
    onSelect: (Timeframe) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Timeframe.entries.forEach { tf ->
            val isSelected = selected == tf
            Button(
                onClick = { onSelect(tf) },
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) primaryColor.copy(alpha = 0.2f) else DarkSurfaceVariant,
                    contentColor = if (isSelected) primaryColor else TextSecondary
                )
            ) {
                Text(
                    text = tf.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun TradingControls(
    selectedOrderType: OrderType,
    onOrderTypeSelected: (OrderType) -> Unit,
    limitPriceInput: String,
    onLimitPriceChanged: (String) -> Unit,
    activationPriceInput: String,
    onActivationPriceChanged: (String) -> Unit,
    trailingPercentInput: String,
    onTrailingPercentChanged: (String) -> Unit,
    sliderPosition: Float,
    onSliderChanged: (Float) -> Unit,
    canBuy: Boolean,
    canSell: Boolean,
    onBuy: () -> Unit,
    onSell: () -> Unit,
    pendingOrders: List<PendingOrder>,
    onCancelPendingOrder: (String) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(DarkCard)
            .padding(14.dp)
    ) {
        // Order Type Selector Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OrderType.entries.forEach { type ->
                val isSelected = selectedOrderType == type
                Button(
                    onClick = { onOrderTypeSelected(type) },
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) primaryColor else DarkSurfaceVariant,
                        contentColor = if (isSelected) DarkBackground else TextSecondary
                    )
                ) {
                    Text(
                        text = type.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Conditional inputs based on Order Type
        when (selectedOrderType) {
            OrderType.MARKET -> {
                // No extra inputs for market
            }

            OrderType.LIMIT -> {
                OutlinedTextField(
                    value = limitPriceInput,
                    onValueChange = onLimitPriceChanged,
                    label = { Text("Limit Price") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = primaryColor,
                        unfocusedLabelColor = TextSecondary
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            OrderType.TRAILING_STOP -> {
                Column {
                    // Activation Price (optional, filled by crosshair)
                    OutlinedTextField(
                        value = activationPriceInput,
                        onValueChange = onActivationPriceChanged,
                        label = { Text("Activation Price (Optional)") },
                        placeholder = { Text("Immediate if empty") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = DarkSurfaceVariant,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = primaryColor,
                            unfocusedLabelColor = TextSecondary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trailing Delta",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("1.0", "2.0", "5.0").forEach { pct ->
                                val isChosen = trailingPercentInput == pct
                                val label = "${pct.toDouble().toInt()}%"
                                Button(
                                    onClick = { onTrailingPercentChanged(pct) },
                                    modifier = Modifier.height(28.dp),
                                    shape = RectangleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isChosen) primaryColor.copy(alpha = 0.25f) else DarkSurfaceVariant,
                                        contentColor = if (isChosen) primaryColor else TextSecondary
                                    )
                                ) {
                                    Text(text = label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = trailingPercentInput,
                        onValueChange = onTrailingPercentChanged,
                        label = { Text("Trailing %") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = DarkSurfaceVariant,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = primaryColor,
                            unfocusedLabelColor = TextSecondary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Slider with percentage label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Amount",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Text(
                text = "${(sliderPosition * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = primaryColor,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = sliderPosition,
            onValueChange = onSliderChanged,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = primaryColor,
                activeTrackColor = primaryColor,
                inactiveTrackColor = DarkSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Buy / Sell buttons with rectangular shape
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val buyText = when (selectedOrderType) {
                OrderType.MARKET -> "BUY"
                OrderType.LIMIT -> "BUY LIMIT"
                OrderType.TRAILING_STOP -> "BUY TRAILING"
            }
            val sellText = when (selectedOrderType) {
                OrderType.MARKET -> "SELL"
                OrderType.LIMIT -> "SELL LIMIT"
                OrderType.TRAILING_STOP -> "SELL TRAILING"
            }

            Button(
                onClick = onBuy,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                enabled = canBuy && sliderPosition > 0f,
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CryptoGreen,
                    contentColor = DarkBackground,
                    disabledContainerColor = CryptoGreenSurface,
                    disabledContentColor = TextTertiary
                )
            ) {
                Text(
                    text = buyText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Button(
                onClick = onSell,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                enabled = canSell && sliderPosition > 0f,
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CryptoRed,
                    contentColor = DarkBackground,
                    disabledContainerColor = CryptoRedSurface,
                    disabledContentColor = TextTertiary
                )
            ) {
                Text(
                    text = sellText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // Active Pending Orders
        if (pendingOrders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Active Orders (${pendingOrders.size})",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            pendingOrders.forEach { order ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sideColor = if (order.side == TradeSide.BUY) CryptoGreen else CryptoRed
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${order.side} ${order.symbol.displayName}",
                                color = sideColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = order.orderType.label,
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        val desc = if (order.orderType == OrderType.LIMIT) {
                            "Target: %.2f (%d%%)".format(order.displayTargetPrice, (order.percentage * 100).toInt())
                        } else {
                            val actInfo = if (order.displayActivationPrice != null) "Act: %.2f | ".format(order.displayActivationPrice) else ""
                            "%sTrail: %.1f%% (%d%%)".format(actInfo, order.trailingPercent ?: 2.0, (order.percentage * 100).toInt())
                        }
                        Text(
                            text = desc,
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    TextButton(
                        onClick = { onCancelPendingOrder(order.id) },
                        shape = RectangleShape,
                        colors = ButtonDefaults.textButtonColors(contentColor = CryptoRed)
                    ) {
                        Text(text = "✕", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
