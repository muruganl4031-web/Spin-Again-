package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import java.util.Random

// --- Core Models ---
enum class RewardType {
    COINS, GEMS, GIFT, JACKPOT, TRY_AGAIN
}

data class RewardItem(
    val id: Int,
    val name: String,
    val type: RewardType,
    val amount: Int,
    val color: Color
)

data class SpinHistoryItem(
    val id: String,
    val rewardName: String,
    val rewardType: RewardType,
    val amount: Int,
    val timestamp: Long
)

data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val color: Color,
    val rotation: Float,
    val rotationSpeed: Float,
    val alpha: Float
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf(0) } // Active Bottom Bar Tab tracking
                val context = LocalContext.current

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        LuckySpinBottomBar(
                            selectedTab = currentScreen,
                            onTabSelected = { index ->
                                currentScreen = index
                                if (index != 0) {
                                    val featureName = when (index) {
                                        1 -> "Rank Leaderboard"
                                        2 -> "Shop Store"
                                        else -> "Profile Options"
                                    }
                                    Toast.makeText(context, "$featureName simulation page!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            0 -> LuckySpinGameScreen()
                            1 -> LeaderboardSimView()
                            2 -> ShopSimView()
                            else -> ProfileSimView()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LuckySpinGameScreen() {
    val coroutineScope = rememberCoroutineScope()
    
    // --- Game States ---
    var coinBalance by remember { mutableStateOf(1240) } // Match start of HTML mockup
    var gemBalance by remember { mutableStateOf(75) }
    var energy by remember { mutableStateOf(85) } // 0 - 100
    var spinsLeft by remember { mutableStateOf(6) }
    var spinHistory by remember { mutableStateOf(listOf<SpinHistoryItem>()) }
    
    var isSpinning by remember { mutableStateOf(false) }
    var showBonusTicketsNotify by remember { mutableStateOf(false) }
    
    // Confetti System
    var isCelebrating by remember { mutableStateOf(false) }
    var confettiParticles by remember { mutableStateOf(listOf<ConfettiParticle>()) }
    
    // Congrats Dialog
    var showCongratsDialog by remember { mutableStateOf(false) }
    var wonReward by remember { mutableStateOf<RewardItem?>(null) }
    var isDoubleClaimed by remember { mutableStateOf(false) }
    var revealMysteryGiftStage by remember { mutableStateOf(false) } // Mystery gift reveal state
    var mysteryGiftOutcome by remember { mutableStateOf<String>("") }
    
    // Settings & Controls
    var soundEnabled by remember { mutableStateOf(true) }
    var hapticsEnabled by remember { mutableStateOf(true) }
    
    // Spin Animation Anchor
    val animatableRotation = remember { Animatable(0f) }
    
    // Defining the 8 Wedge Rewards matched to original sequence but loaded with Polish Theme color segments
    val rewards = remember {
        listOf(
            RewardItem(0, "100 COINS", RewardType.COINS, 100, Color(0xFFE040FB)), // Violet Purple
            RewardItem(1, "15 GEMS", RewardType.GEMS, 15, Color(0xFF40C4FF)),   // Aqua Blue
            RewardItem(2, "MYSTERY GIFT", RewardType.GIFT, 1, Color(0xFFFFAB40)), // Orange Gold
            RewardItem(3, "50 COINS", RewardType.COINS, 50, Color(0xFF7C4DFF)),  // Deep Indigo Purple
            RewardItem(4, "JACKPOT 1000", RewardType.JACKPOT, 1000, Color(0xFFFF5252)), // Coral Deep Red
            RewardItem(5, "50 GEMS", RewardType.GEMS, 50, Color(0xFF448AFF)),   // Cobalt Blue
            RewardItem(6, "TRY AGAIN", RewardType.TRY_AGAIN, 0, Color(0xFFFFD740)), // Vibrant Gold Yellow
            RewardItem(7, "200 COINS", RewardType.COINS, 200, Color(0xFF69F0AE)) // Mint Green
        )
    }

    // Measure utilities
    val textMeasurer = rememberTextMeasurer()

    // Background floating stars layout mapping (Random static constellation)
    val starCoords = remember {
        val r = Random(42)
        List(25) {
            Pair(r.nextFloat(), r.nextFloat())
        }
    }

    // Gravity / Motion Loop for Custom Celebration Confetti
    LaunchedEffect(isCelebrating) {
        if (isCelebrating) {
            val random = java.util.Random()
            confettiParticles = List(55) {
                val angle = random.nextFloat() * 2 * Math.PI
                val speed = 6f + random.nextFloat() * 16f
                ConfettiParticle(
                    x = 0f,
                    y = 0f,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed - 6f).toFloat(), // eject vertical nudge
                    size = 12f + random.nextFloat() * 12f,
                    color = Color(
                        150 + random.nextInt(106),
                        80 + random.nextInt(120),
                        180 + random.nextInt(75)
                    ),
                    rotation = random.nextFloat() * 360f,
                    rotationSpeed = -15f + random.nextFloat() * 30f,
                    alpha = 1.0f
                )
            }

            var lastTime = withFrameNanos { it }
            while (isCelebrating) {
                withFrameNanos { time ->
                    val dt = ((time - lastTime) / 1_000_000_000f).coerceAtMost(0.04f)
                    lastTime = time
                    confettiParticles = confettiParticles.map { p ->
                        p.copy(
                            x = p.x + p.vx * 60f * dt,
                            y = p.y + p.vy * 60f * dt,
                            vy = p.vy + 0.35f, // gravity accel
                            rotation = p.rotation + p.rotationSpeed * 60f * dt,
                            alpha = (p.alpha - dt * 0.45f).coerceAtLeast(0f)
                        )
                    }.filter { it.alpha > 0f && it.y < 1200f }
                }
                if (confettiParticles.isEmpty()) {
                    isCelebrating = false
                }
            }
        } else {
            confettiParticles = emptyList()
        }
    }

    // Master Spinning Core Logic
    fun startSpinWheel() {
        if (isSpinning || spinsLeft <= 0) return
        
        isSpinning = true
        isDoubleClaimed = false
        showCongratsDialog = false
        revealMysteryGiftStage = false
        mysteryGiftOutcome = ""
        spinsLeft -= 1
        
        // Subtract energy to keep gameplay realistic
        energy = (energy - 15).coerceAtLeast(0)

        // Select a designated winning item index gracefully
        val rng = Random()
        val targetIndex = rng.nextInt(8) // Pick slice 0 to 7
        val selectedItem = rewards[targetIndex]
        wonReward = selectedItem

        // Core physics calculation
        val currentRot = animatableRotation.value
        val revolutions = 6 + rng.nextInt(3) // 6 to 8 spins
        
        // Exact angle matching top 12 o'clock pointer:
        // angleOffset for slice index: (247.5 - index * 45) degrees
        val targetSpecificStopAngle = ((247.5f - targetIndex * 45f) + 360f) % 360f
        val destinationRotation = currentRot + (revolutions * 360f) + (targetSpecificStopAngle - (currentRot % 360f))

        coroutineScope.launch {
            // Smoothly ease standard decelerating scroll with high-fidelity curves
            animatableRotation.animateTo(
                targetValue = destinationRotation,
                animationSpec = tween(
                    durationMillis = 4200,
                    easing = CubicBezierEasing(0.12f, 0.88f, 0.22f, 1f)
                )
            )

            // Trigger Victory!
            isSpinning = false
            isCelebrating = true
            showCongratsDialog = true

            // Trigger corresponding history action
            val outcomeItem = SpinHistoryItem(
                id = "spin_${System.currentTimeMillis()}",
                rewardName = selectedItem.name,
                rewardType = selectedItem.type,
                amount = selectedItem.amount,
                timestamp = System.currentTimeMillis()
            )
            spinHistory = listOf(outcomeItem) + spinHistory

            // Apply direct balance updates automatically unless Claim allows multiplier
            if (selectedItem.type != RewardType.GIFT && selectedItem.type != RewardType.TRY_AGAIN) {
                if (selectedItem.type == RewardType.COINS || selectedItem.type == RewardType.JACKPOT) {
                    coinBalance += selectedItem.amount
                } else if (selectedItem.type == RewardType.GEMS) {
                    gemBalance += selectedItem.amount
                }
            } else if (selectedItem.type == RewardType.TRY_AGAIN) {
                spinsLeft += 1
                energy = (energy + 10).coerceAtMost(100)
                showBonusTicketsNotify = true
            }
        }
    }

    // Modern Light Lilac Base Gradient: #FEF7FF
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFEF7FF), // Pale light surface
                        Color(0xFFF6EEFF)  // Subtly deeper lilac base
                    )
                )
            )
    ) {

        // --- BACKGROUND CHIC SPARKS ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pulse = (sin(System.currentTimeMillis() / 400.0) * 0.35 + 0.65).toFloat()
            starCoords.forEach { (x, y) ->
                drawCircle(
                    color = Color(0xFF6750A4).copy(alpha = 0.08f * pulse),
                    radius = 2.dp.toPx(),
                    center = Offset(x * size.width, y * size.height)
                )
            }
        }

        // --- MAIN SCROLL CONTENT LAYER ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // --- HEADER ACTION ROW AS DIALOG HEADER ---
            HeaderActionBar()

            // --- HEADER METRICS BAR OUTLINE CARDS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Coins balance indicator
                PolishMetricCard(
                    tag = "coin_balance_card",
                    icon = Icons.Default.MonetizationOn,
                    iconColor = Color(0xFFFFB300),
                    label = "Coins",
                    value = String.format("%,d", coinBalance)
                )

                // Gems balance indicator
                PolishMetricCard(
                    tag = "gem_balance_card",
                    icon = Icons.Default.Diamond,
                    iconColor = Color(0xFF00E5FF),
                    label = "Gems",
                    value = gemBalance.toString()
                )

                // Active Spins left tracker
                PolishMetricCard(
                    tag = "spins_left_card",
                    icon = Icons.Default.LocalActivity,
                    iconColor = Color(0xFFE040FB),
                    label = "Spins",
                    value = "$spinsLeft left"
                )
            }

            // Energy Level visual modern dynamic slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 10.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Energy indicator",
                            tint = Color(0xFF6750A4),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ENERGY RESERVE",
                            style = TextStyle(
                                color = Color(0xFF49454F),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                    Text(
                        text = "$energy/100",
                        style = TextStyle(
                            color = Color(0xFF6750A4),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                // Glass reservoir slider track
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFFCAC4D0).copy(alpha = 0.25f))
                        .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.4f), RoundedCornerShape(5.dp))
                ) {
                    val animatedPercent by animateFloatAsState(targetValue = energy / 100f, label = "energy_anim")
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedPercent)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFEADDFF),
                                        Color(0xFF6750A4)
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- HEADER TEXT LOGO IN MODERN PILL CHIP ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFFEADDFF), RoundedCornerShape(50.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stars,
                        contentDescription = "Stars Icon",
                        tint = Color(0xFF21005D),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LUCKY SPIN",
                        style = TextStyle(
                            color = Color(0xFF21005D),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Spin the wheel to win diamonds,\ncoins, and exclusive chests!",
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        color = Color(0xFF49454F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 18.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // --- LUCK SPIN WHEEL ARTWORK AREA ---
            Box(
                modifier = Modifier
                    .size(340.dp)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                val rotationAngle = animatableRotation.value

                // Computed pointer ticking deflection
                val dividerProgress = (rotationAngle + 22.5f) % 45f
                val pointerDeflection = if (dividerProgress < 12f) {
                    val progress = dividerProgress / 12f
                    if (progress < 0.25f) (progress / 0.25f) * 16f
                    else (1.0f - (progress - 0.25f) / 0.75f) * 16f
                } else 0f

                // Draw wheel graphics with deep 3D outlines and golds
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2f * 0.95f

                    // 1. Sleek Shadow Layer under the wheel base
                    drawCircle(
                        color = Color(0xFF1D1B20).copy(alpha = 0.22f),
                        radius = radius + 6.dp.toPx(),
                        center = center + Offset(0f, 6.dp.toPx())
                    )

                    // 2. Draw Wedges (8 Equal sectors rotated dynamically)
                    drawContext.canvas.save()
                    drawContext.canvas.rotate(rotationAngle, center.x, center.y)

                    val sliceAngle = 45f
                    for (i in 0 until 8) {
                        val item = rewards[i]
                        val startArc = i * 360f / 8f
                        
                        // Draw colorful wedge slices
                        drawArc(
                            color = item.color,
                            startAngle = startArc,
                            sweepAngle = sliceAngle,
                            useCenter = true,
                            size = Size(radius * 2, radius * 2),
                            topLeft = Offset(center.x - radius, center.y - radius)
                        )

                        // Highlight wedge accent shade overlays
                        drawArc(
                            color = Color.Black.copy(alpha = 0.05f),
                            startAngle = startArc,
                            sweepAngle = sliceAngle,
                            useCenter = true,
                            size = Size(radius * 1.9f, radius * 1.9f),
                            topLeft = Offset(center.x - radius * 0.95f, center.y - radius * 0.95f)
                        )

                        // 3. Draw Wedge Labels Symmetrically Radial
                        val labelAngle = startArc + sliceAngle / 2f
                        val textRadius = radius * 0.52f
                        val textLayout = textMeasurer.measure(
                            text = item.name,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center
                            )
                        )
                        val textW = textLayout.size.width
                        val textH = textLayout.size.height

                        drawContext.canvas.save()
                        drawContext.canvas.rotate(labelAngle, center.x, center.y)
                        drawContext.canvas.translate(center.x + textRadius, center.y)
                        drawContext.canvas.rotate(90f) // Orient upright

                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(-textW / 2f, -textH / 2f)
                        )

                        // Draw Custom Vectors directly inside segments
                        val iconRadius = radius * 0.32f
                        drawContext.canvas.restore()
                        drawContext.canvas.save()
                        drawContext.canvas.rotate(labelAngle, center.x, center.y)
                        drawContext.canvas.translate(center.x + iconRadius, center.y)
                        drawContext.canvas.rotate(90f)

                        drawWedgeCustomVectorIcon(
                            type = item.type,
                            pxSize = 24.dp.toPx(),
                            badgeColor = item.color
                        )
                        drawContext.canvas.restore()
                    }

                    // 4. Shiny Gold dividers
                    for (i in 0 until 8) {
                        val divAngle = i * 45f
                        val divRad = divAngle * Math.PI / 180.0
                        val endpoint = Offset(
                            (center.x + radius * cos(divRad)).toFloat(),
                            (center.y + radius * sin(divRad)).toFloat()
                        )
                        drawLine(
                            color = Color(0xFFFFD700), // Gold
                            start = center,
                            end = endpoint,
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                    drawContext.canvas.restore()

                    // 5. Deluxe outer polished Gold ring bezel containing LEDS
                    drawCircle(
                        color = Color(0xFFFFD700), // Bezel Gold
                        radius = radius + 3.dp.toPx(),
                        center = center,
                        style = Stroke(width = 8.dp.toPx())
                    )

                    // 6. Blinking Chasing LED bulbs inside bezel
                    val ledCount = 16
                    val bulbSpacingAngle = 360f / ledCount.toFloat()
                    val bulbActiveColor = Color(0xFFFFF176) // Glowing yellow
                    val bulbInactiveColor = Color(0xFFB8860B) // Dark gold base
                    
                    val activeLedIndex = when {
                        isSpinning -> ((rotationAngle / 15f).toInt()) % ledCount
                        isCelebrating -> ((System.currentTimeMillis() / 150f).toInt()) % ledCount
                        else -> ((System.currentTimeMillis() / 400f).toInt()) % ledCount
                    }
                    
                    val animatePulsingBlink = !isSpinning && !isCelebrating && 
                                               (((System.currentTimeMillis() / 600f).toInt() % 2) == 0)

                    for (j in 0 until ledCount) {
                        val ledAngle = j * bulbSpacingAngle - 90f
                        val ledRad = ledAngle * Math.PI / 180.0
                        val bulbRadius = radius + 3.dp.toPx()
                        val bulbCenter = Offset(
                            (center.x + bulbRadius * cos(ledRad)).toFloat(),
                            (center.y + bulbRadius * sin(ledRad)).toFloat()
                        )

                        val isBulbOn = (j == activeLedIndex) || (isCelebrating && activeLedIndex % 2 == j % 2) || (animatePulsingBlink)
                        
                        if (isBulbOn) {
                            drawCircle(
                                color = bulbActiveColor.copy(alpha = 0.4f),
                                radius = 7.dp.toPx(),
                                center = bulbCenter
                            )
                            drawCircle(
                                color = bulbActiveColor,
                                radius = 3.5.dp.toPx(),
                                center = bulbCenter
                            )
                        } else {
                            drawCircle(
                                color = bulbInactiveColor,
                                radius = 3.dp.toPx(),
                                center = bulbCenter
                            )
                        }
                    }
                }

                // --- 3D GLOSSY SPIN BUTTON FROM THE HTML PROTOTYPE ---
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            enabled = !isSpinning && spinsLeft > 0
                        ) {
                            startSpinWheel()
                        }
                        .testTag("spin_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val btnCenter = Offset(size.width / 2f, size.height / 2f)
                        val btnRadius = size.width / 2f * 0.93f

                        // Shadow deep 3D golden bevel bottom border
                        drawCircle(
                            color = Color(0xFF8B6508), // Dark gold bronze shadow
                            radius = btnRadius,
                            center = btnCenter + Offset(0f, 4.dp.toPx())
                        )

                        // Outer gradient ring: from(#FFED4B) to(#D4A017)
                        drawCircle(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFFFED4B), Color(0xFFD4A017)),
                                startY = btnCenter.y - btnRadius,
                                endY = btnCenter.y + btnRadius
                            ),
                            radius = btnRadius,
                            center = btnCenter
                        )

                        // Inner core face: from-[#B8860B] to-[#FFD700] rotated
                        drawCircle(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFFFD700), Color(0xFFB8860B)),
                                startY = btnCenter.y - (btnRadius - 4.dp.toPx()),
                                endY = btnCenter.y + (btnRadius - 4.dp.toPx())
                            ),
                            radius = btnRadius - 4.dp.toPx(),
                            center = btnCenter
                        )

                        // Highlight glossy white crest detail
                        drawArc(
                            color = Color.White.copy(alpha = 0.45f),
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            size = Size((btnRadius * 1.5f), (btnRadius * 1f)),
                            topLeft = Offset(btnCenter.x - btnRadius * 0.75f, btnCenter.y - btnRadius * 0.9f),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }

                    // Button title Coffee brown text
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SPIN",
                            style = TextStyle(
                                color = if (isSpinning || spinsLeft <= 0) Color(0xFF7D6B60) else Color(0xFF4E342E),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                shadow = Shadow(
                                    color = Color.White.copy(alpha = 0.5f),
                                    offset = Offset(0f, 1f),
                                    blurRadius = 1f
                                )
                            )
                        )
                        Text(
                            text = if (isSpinning) "ROLL" else if (spinsLeft == 0) "EMPTY" else "PLAY",
                            style = TextStyle(
                                color = if (isSpinning || spinsLeft <= 0) Color(0xFF8D7B70) else Color(0xFF795548),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        )
                    }
                }

                // --- STYLIZED GOLDEN ARROW PHYSICAL POINTER AT THE TOP ---
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-6).dp)
                        .size(32.dp)
                        .height(40.dp)
                        .rotate(pointerDeflection),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Stylized Pointer
                        val path = Path().apply {
                            moveTo(w / 2f, h * 0.9f)             // Pointer Bottom center tip
                            lineTo(w * 0.15f, h * 0.2f)          // Top Left corner
                            lineTo(w * 0.35f, h * 0.2f)          // Inner stem Left
                            lineTo(w * 0.35f, 0f)                 // Top Left neck edge
                            lineTo(w * 0.65f, 0f)                 // Top Right neck edge
                            lineTo(w * 0.65f, h * 0.2f)          // Inner stem Right
                            lineTo(w * 0.85f, h * 0.2f)          // Top Right corner
                            close()
                        }

                        // Cast 3D shadow list
                        drawPath(
                            path = path,
                            color = Color.Black.copy(alpha = 0.2f),
                            alpha = 1.0f
                        )

                        // Main Pointer gradient matching bottom golden layout
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFFED4B),
                                    Color(0xFFD4A017)
                                )
                            )
                        )

                        // Gold highlighted borders
                        drawPath(
                            path = path,
                            color = Color(0xFFB8860B),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- REFILL / BALANCE CARD FROM HTML THEME ---
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.8f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF6750A4)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = "Wallet icon",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "BALANCE",
                                style = TextStyle(
                                    color = Color(0xFF49454F),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = String.format("%,d", coinBalance),
                                    style = TextStyle(
                                        color = Color(0xFF1D1B20),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "COINS",
                                    style = TextStyle(
                                        color = Color(0xFF6750A4),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (!isSpinning) {
                                if (energy >= 20) {
                                    energy -= 20
                                    spinsLeft += 2
                                    coinBalance += 100
                                } else {
                                    energy = (energy + 30).coerceAtMost(100)
                                    spinsLeft += 1
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6750A4)
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Text(
                            text = "Refill",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            // --- SETTINGS CONTROLLER ACTIONS BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PolishToggleButton(
                    icon = if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    active = soundEnabled,
                    title = "Sounds"
                ) {
                    soundEnabled = !soundEnabled
                }

                PolishToggleButton(
                    icon = if (hapticsEnabled) Icons.Default.Vibration else Icons.Default.PortableWifiOff,
                    active = hapticsEnabled,
                    title = "Haptic Feed"
                ) {
                    hapticsEnabled = !hapticsEnabled
                }

                PolishToggleButton(
                    icon = Icons.Default.Refresh,
                    active = false,
                    title = "Reset Play"
                ) {
                    coinBalance = 1240
                    gemBalance = 75
                    energy = 85
                    spinsLeft = 6
                    spinHistory = emptyList()
                    showCongratsDialog = false
                    isCelebrating = false
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // --- RECENT WINS LIST SECTION ---
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(bottom = 36.dp)
                    .testTag("recent_wins_list")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT SPIN HISTORY",
                        style = TextStyle(
                            color = Color(0xFF1D1B20),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "LEDGER",
                        style = TextStyle(
                            color = Color(0xFF6750A4),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (spinHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Casino,
                                contentDescription = "Play icon template placeholder",
                                tint = Color(0xFF6750A4).copy(alpha = 0.25f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No spins tracked yet. Press SPIN!",
                                style = TextStyle(
                                    color = Color(0xFF49454F),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        spinHistory.take(5).forEach { recordItem ->
                            PolishSpinHistoryRow(item = recordItem)
                        }
                    }
                }
            }
        }

        // --- CONGRATS CELEBRATION MODAL OVERLAY ---
        if (showCongratsDialog && wonReward != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) {}, // Consume taps under modal
                contentAlignment = Alignment.Center
            ) {
                val reward = wonReward!!
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFFD54F), Color(0xFFFF8F00))
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (reward.type == RewardType.TRY_AGAIN) "BONUS TURN!" else "CONGRATULATIONS!",
                            style = TextStyle(
                                color = Color(0xFFB8860B),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (reward.type == RewardType.TRY_AGAIN) "The wheel grants fortune!" else "You won a spectacular prize!",
                            style = TextStyle(
                                color = Color(0xFF49454F),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Render prize
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            reward.color.copy(alpha = 0.2f),
                                            Color.Transparent
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = reward.color.copy(alpha = 0.08f),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = true
                                )
                                drawWedgeCustomVectorIcon(
                                    type = reward.type,
                                    pxSize = 72.dp.toPx(),
                                    badgeColor = reward.color
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (reward.type == RewardType.GIFT) {
                            if (!revealMysteryGiftStage) {
                                Text(
                                    text = "MYSTERY CHEST PACK",
                                    style = TextStyle(
                                        color = Color(0xFF1D1B20),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        val mysteryItems = listOf(
                                            "300 Coins + 2 Bonus Tickets!",
                                            "150 Coins + 10 Gems!",
                                            "Double Energy Refill! (+50⚡)",
                                            "JACKPOT REFUND: 500 Coins!"
                                        )
                                        val outputSelectedRef = mysteryItems[Random().nextInt(4)]
                                        mysteryGiftOutcome = outputSelectedRef
                                        revealMysteryGiftStage = true

                                        // Apply rewards manually for Gift packs
                                        when {
                                            outputSelectedRef.contains("300") -> {
                                                coinBalance += 300
                                                spinsLeft += 2
                                            }
                                            outputSelectedRef.contains("150") -> {
                                                coinBalance += 150
                                                gemBalance += 10
                                            }
                                            outputSelectedRef.contains("Double") -> {
                                                energy = (energy + 50).coerceAtMost(100)
                                            }
                                            outputSelectedRef.contains("JACKPOT") -> {
                                                coinBalance += 500
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF6750A4)
                                    ),
                                    shape = RoundedCornerShape(50.dp),
                                    modifier = Modifier.testTag("claim_button")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("TAP TO REVEAL GIFT", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Text(
                                    text = "YOU OPENED & WON:",
                                    style = TextStyle(
                                        color = Color(0xFF6750A4),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = mysteryGiftOutcome,
                                    style = TextStyle(
                                        color = Color(0xFF1D1B20),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.Center
                                    )
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = {
                                        showCongratsDialog = false
                                        isCelebrating = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF6750A4)
                                    ),
                                    shape = RoundedCornerShape(50.dp)
                                ) {
                                    Text("AWESOME!", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Text(
                                text = reward.name,
                                style = TextStyle(
                                    color = Color(0xFF1D1B20),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                )
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showCongratsDialog = false
                                        isCelebrating = false
                                    },
                                    border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("claim_button"),
                                    shape = RoundedCornerShape(50.dp)
                                ) {
                                    Text(
                                        text = "CLAIM",
                                        color = Color(0xFF1D1B20),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (!isDoubleClaimed) {
                                            isDoubleClaimed = true
                                            if (reward.type == RewardType.COINS || reward.type == RewardType.JACKPOT) {
                                                coinBalance += reward.amount
                                            } else if (reward.type == RewardType.GEMS) {
                                                gemBalance += reward.amount
                                            }
                                        }
                                        showCongratsDialog = false
                                        isCelebrating = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF6750A4)
                                    ),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("double_reward_button"),
                                    shape = RoundedCornerShape(50.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stars,
                                            contentDescription = "Double stars",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "CLAIM 2X",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- CONFETTI PARTICLE RENDERER ---
        if (confettiParticles.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val screenCenter = Offset(size.width / 2f, size.height / 2f)
                confettiParticles.forEach { p ->
                    drawContext.canvas.save()
                    drawContext.canvas.translate(screenCenter.x + p.x, screenCenter.y + p.y)
                    drawContext.canvas.rotate(p.rotation)
                    
                    drawRect(
                        color = p.color.copy(alpha = p.alpha),
                        topLeft = Offset(-p.size / 2f, -p.size / 2f),
                        size = Size(p.size, p.size * 0.7f)
                    )
                    drawContext.canvas.restore()
                }
            }
        }

        // --- TOAST NOTIFICATION ---
        AnimatedVisibility(
            visible = showBonusTicketsNotify,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF69F0AE)
                ),
                border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.2f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Stars, contentDescription = "Lucky star", tint = Color(0xFF21005D))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VIRTUAL REFILL: +1 Free Spin Ticket!",
                        color = Color(0xFF21005D),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
            LaunchedEffect(showBonusTicketsNotify) {
                delay(2800)
                showBonusTicketsNotify = false
            }
        }
    }
}

// --- Dynamic Vector Wedges Drawing Logic ---
fun DrawScope.drawWedgeCustomVectorIcon(type: RewardType, pxSize: Float, badgeColor: Color) {
    val center = Offset(0f, 0f)
    val sz = pxSize

    when (type) {
        RewardType.COINS -> {
            drawOval(
                color = Color(0xFF8B6508),
                topLeft = Offset(-sz * 0.5f, -sz * 0.25f),
                size = Size(sz, sz * 0.5f)
            )
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF176), Color(0xFFF57F17)),
                    center = center,
                    radius = sz * 0.5f
                ),
                topLeft = Offset(-sz * 0.45f, -sz * 0.3f),
                size = Size(sz * 0.9f, sz * 0.45f)
            )
            drawOval(
                color = Color(0xFFFFD54F),
                topLeft = Offset(-sz * 0.22f, -sz * 0.17f),
                size = Size(sz * 0.44f, sz * 0.22f),
                style = Stroke(width = 1.3f.dp.toPx())
            )
        }
        RewardType.GEMS -> {
            val dPath = Path().apply {
                moveTo(0f, -sz * 0.5f)
                lineTo(sz * 0.45f, -sz * 0.1f)
                lineTo(0f, sz * 0.5f)
                lineTo(-sz * 0.45f, -sz * 0.1f)
                close()
            }
            drawPath(
                path = dPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF80DEEA), Color(0xFF00ACC1))
                )
            )
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(-sz * 0.15f, -sz * 0.1f),
                end = Offset(0f, -sz * 0.4f),
                strokeWidth = 2f.dp.toPx()
            )
        }
        RewardType.GIFT -> {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFEC407A), Color(0xFFC2185B))
                ),
                topLeft = Offset(-sz * 0.4f, -sz * 0.35f),
                size = Size(sz * 0.8f, sz * 0.7f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            drawRect(
                color = Color(0xFFFFD54F),
                topLeft = Offset(-sz * 0.08f, -sz * 0.35f),
                size = Size(sz * 0.16f, sz * 0.7f)
            )
            drawRect(
                color = Color(0xFFFFD54F),
                topLeft = Offset(-sz * 0.4f, -sz * 0.08f),
                size = Size(sz * 0.8f, sz * 0.16f)
            )
            drawCircle(
                color = Color(0xFFFFCA28),
                radius = sz * 0.14f,
                center = Offset(0f, -sz * 0.38f)
            )
        }
        RewardType.JACKPOT -> {
            drawRoundRect(
                color = Color(0xFF4E342E),
                topLeft = Offset(-sz * 0.48f, -sz * 0.1f),
                size = Size(sz * 0.96f, sz * 0.55f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF8D6E63), Color(0xFF5D4037))
                ),
                topLeft = Offset(-sz * 0.44f, -sz * 0.08f),
                size = Size(sz * 0.88f, sz * 0.5f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            drawArc(
                color = Color(0xFFD4AF37),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(-sz * 0.44f, -sz * 0.36f),
                size = Size(sz * 0.88f, sz * 0.55f)
            )
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = sz * 0.1f,
                center = Offset(0f, -sz * 0.04f)
            )
        }
        RewardType.TRY_AGAIN -> {
            val leafRad = sz * 0.22f
            drawCircle(color = Color(0xFF2E7D32), radius = leafRad, center = Offset(-sz * 0.13f, -sz * 0.11f))
            drawCircle(color = Color(0xFF2E7D32), radius = leafRad, center = Offset(sz * 0.13f, -sz * 0.11f))
            drawCircle(color = Color(0xFF2E7D32), radius = leafRad, center = Offset(-sz * 0.13f, sz * 0.11f))
            drawCircle(color = Color(0xFF2E7D32), radius = leafRad, center = Offset(sz * 0.13f, sz * 0.11f))
            
            drawLine(
                color = Color(0xFF1B5E20),
                start = Offset(0f, 0f),
                end = Offset(sz * 0.22f, sz * 0.44f),
                strokeWidth = 3f.dp.toPx()
            )
        }
    }
}

// --- POLISHED HEADER BAR ---
@Composable
fun HeaderActionBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Go back",
                tint = Color(0xFF1D1B20)
            )
        }
        Text(
            text = "Daily Rewards",
            style = TextStyle(
                color = Color(0xFF1D1B20),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
        )
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color(0xFF1D1B20)
            )
        }
    }
}

// --- METRIC CARD OUTLINE STYLE ---
@Composable
fun PolishMetricCard(
    tag: String,
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String
) {
    Box(
        modifier = Modifier
            .width(104.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag(tag)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label.uppercase(),
                    style = TextStyle(
                        color = Color(0xFF49454F),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = TextStyle(
                    color = Color(0xFF1D1B20),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

// --- POLISHED TOGGLE BUTTON SIMULATION ---
@Composable
fun PolishToggleButton(
    icon: ImageVector,
    active: Boolean,
    title: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (active) Color(0xFFEADDFF) else Color.White)
                .border(
                    width = 1.dp,
                    color = if (active) Color(0xFF6750A4) else Color(0xFFCAC4D0),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (active) Color(0xFF21005D) else Color(0xFF49454F),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = TextStyle(
                color = Color(0xFF49454F),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

// --- SPIN HISTORY ROW ---
@Composable
fun PolishSpinHistoryRow(item: SpinHistoryItem) {
    val displayIcon = when (item.rewardType) {
        RewardType.COINS, RewardType.JACKPOT -> Icons.Default.MonetizationOn
        RewardType.GEMS -> Icons.Default.Diamond
        RewardType.GIFT -> Icons.Default.CardGiftcard
        RewardType.TRY_AGAIN -> Icons.Default.Stars
    }
    val badgeTint = when (item.rewardType) {
        RewardType.COINS -> Color(0xFFFFB300)
        RewardType.GEMS -> Color(0xFF00E5FF)
        RewardType.GIFT -> Color(0xFFE040FB)
        RewardType.JACKPOT -> Color(0xFFFF5252)
        RewardType.TRY_AGAIN -> Color(0xFFFFD740)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(badgeTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = displayIcon,
                        contentDescription = item.rewardName,
                        tint = badgeTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = item.rewardName,
                    style = TextStyle(
                        color = Color(0xFF1D1B20),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Text(
                text = "JUST NOW",
                style = TextStyle(
                    color = Color(0xFF49454F).copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

// --- NEW SYSTEM BOTTOM NAVIGATION BAR (M3 SPEC) ---
@Composable
fun LuckySpinBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = Color(0xFFCAC4D0).copy(alpha = 0.5f))
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        val items = listOf(
            Triple(Icons.Default.Home, "Home", 0),
            Triple(Icons.Default.Leaderboard, "Rank", 1),
            Triple(Icons.Default.ShoppingBag, "Shop", 2),
            Triple(Icons.Default.Person, "Profile", 3)
        )
        items.forEach { (icon, label, index) ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selectedTab == index) Color(0xFF6750A4) else Color(0xFF49454F).copy(alpha = if (index == 2) 0.5f else 1f)
                    )
                },
                label = {
                    Text(
                        text = label,
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedTab == index) Color(0xFF6750A4) else Color(0xFF49454F).copy(alpha = if (index == 2) 0.5f else 1f)
                        )
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color(0xFFEADDFF)
                )
            )
        }
    }
}

// --- SUB-SCREEN SIMULATIONS FOR HIGH-END INTERACTION ---
@Composable
fun LeaderboardSimView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderActionBar()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "RANKING LEADERBOARD",
            style = TextStyle(color = Color(0xFF21005D), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(16.dp))
        val sampleRankers = listOf(
            Triple(1, "GoldenSparks", "43,200 Coins"),
            Triple(2, "DiamondGamer", "39,150 Coins"),
            Triple(3, "LuckyStar", "31,000 Coins"),
            Triple(4, "SpinMaster", "25,400 Coins"),
            Triple(5, "JackpotKing", "22,100 Coins")
        )
        sampleRankers.forEach { (rank, name, score) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (rank <= 3) Color(0xFFEADDFF) else Color(0xFFCAC4D0).copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#$rank",
                                fontWeight = FontWeight.ExtraBold,
                                color = if (rank <= 3) Color(0xFF21005D) else Color(0xFF49454F),
                                fontSize = 12.sp
                            )
                        }
                        Text(text = name, fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20))
                    }
                    Text(text = score, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6750A4))
                }
            }
        }
    }
}

@Composable
fun ShopSimView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderActionBar()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "COSMIC SHOP SUPPLY",
            style = TextStyle(color = Color(0xFF21005D), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(16.dp))
        val shopItems = listOf(
            Triple("1,000 Coins Pack", "Get a massive chest of coins instantly", "$0.99"),
            Triple("50 Gems Sack", "Expand your precious gems supply", "$1.99"),
            Triple("Infinite Energy Refill", "Instantly top up complete reserves to 100", "$2.99")
        )
        shopItems.forEach { (title, subtitle, price) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20), fontSize = 15.sp)
                        Text(text = subtitle, color = Color(0xFF49454F), fontSize = 11.sp)
                    }
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
                    ) {
                        Text(text = price, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileSimView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderActionBar()
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFFEADDFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Default.Person, contentDescription = "Profile", tint = Color(0xFF21005D), modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Lucky Tester Account", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF1D1B20))
        Text(text = "ID: #99321-A", color = Color(0xFF49454F), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Spins Executed", color = Color(0xFF49454F), fontSize = 13.sp)
                    Text("42", fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20), fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Jackpots Claimed", color = Color(0xFF49454F), fontSize = 13.sp)
                    Text("2", fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20), fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Current Play League", color = Color(0xFF49454F), fontSize = 13.sp)
                    Text("Gold Tier V", fontWeight = FontWeight.Bold, color = Color(0xFF6750A4), fontSize = 13.sp)
                }
            }
        }
    }
}
