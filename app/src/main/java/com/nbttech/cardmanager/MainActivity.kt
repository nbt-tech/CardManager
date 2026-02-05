package com.nbttech.cardmanager

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

class MainActivity : ComponentActivity() {

    private lateinit var cardViewModel: CardViewModel

    private val exportJsonLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportDataJson(it) }
    }

    private val exportCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { exportDataCsv(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importData(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CardManagerTheme {
                val navController = rememberNavController()
                cardViewModel = viewModel()

                NavHost(navController = navController, startDestination = "card_list") {
                    composable("card_list") {
                        CardListScreen(
                            navController = navController,
                            viewModel = cardViewModel,
                            onExportJson = { exportJsonLauncher.launch("cards_backup.json") },
                            onExportCsv = { exportCsvLauncher.launch("cards_backup.csv") },
                            onImport = { importLauncher.launch(arrayOf("application/json", "text/csv", "text/comma-separated-values")) }
                        )
                    }
                    composable("card_input") {
                        CardInputScreen(navController, cardViewModel)
                    }
                }
            }
        }
    }

    private fun exportDataJson(uri: Uri) {
        lifecycleScope.launch {
            val cards = cardViewModel.getAllCardsSync()
            val json = Gson().toJson(cards)
            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
            Toast.makeText(this@MainActivity, "Exported as JSON", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportDataCsv(uri: Uri) {
        lifecycleScope.launch {
            val cards = cardViewModel.getAllCardsSync()
            val csv = StringBuilder("cardName,cardNumber,expiryDate,cvv,brand,issuer\n")
            cards.forEach {
                csv.append("${it.cardName},${it.cardNumber},${it.expiryDate},${it.cvv},${it.brand},${it.issuer}\n")
            }
            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri)?.use { it.write(csv.toString().toByteArray()) }
            }
            Toast.makeText(this@MainActivity, "Exported as CSV", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importData(uri: Uri) {
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: return@launch

                val cards = if (content.trim().startsWith("[")) {
                    // JSON
                    val type = object : TypeToken<List<CardEntity>>() {}.type
                    Gson().fromJson<List<CardEntity>>(content, type)
                } else {
                    // CSV
                    val lines = content.lines()
                    if (lines.size > 1) {
                        lines.drop(1).filter { it.isNotBlank() }.map { line ->
                            val parts = line.split(",")
                            CardEntity(
                                cardName = parts.getOrNull(0) ?: "",
                                cardNumber = parts.getOrNull(1) ?: "",
                                expiryDate = parts.getOrNull(2) ?: "",
                                cvv = parts.getOrNull(3) ?: "",
                                brand = parts.getOrNull(4) ?: "",
                                issuer = parts.getOrNull(5) ?: ""
                            )
                        }
                    } else emptyList()
                }

                if (cards.isNotEmpty()) {
                    cardViewModel.importCards(cards)
                    Toast.makeText(this@MainActivity, "Imported ${cards.size} cards", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Import failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun CardManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF080808),
            surface = Color(0xFF121212),
            primary = Color(0xFF00F2FF)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080808)) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CardListScreen(
    navController: NavController,
    viewModel: CardViewModel,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImport: () -> Unit
) {
    val dbCards by viewModel.allCards.collectAsState(initial = emptyList())
    var listForDisplay by remember { mutableStateOf(emptyList<CardEntity>()) }
    
    LaunchedEffect(dbCards) {
        listForDisplay = dbCards
    }

    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var selectedCard by remember { mutableStateOf<CardEntity?>(null) }
    var isEditMode by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    val lazyListState = rememberLazyListState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("MY VAULT", style = MaterialTheme.typography.labelLarge, color = Color(0xFF00F2FF), letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Import/Export", tint = Color(0xFF00F2FF))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color(0xFF1A1A1A))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export JSON", color = Color.White) },
                                onClick = { showMenu = false; onExportJson() },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF00F2FF)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Export CSV", color = Color.White) },
                                onClick = { showMenu = false; onExportCsv() },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF00F2FF)) }
                            )
                            Divider(color = Color.White.copy(alpha = 0.1f))
                            DropdownMenuItem(
                                text = { Text("Import File", color = Color.White) },
                                onClick = { showMenu = false; onImport() },
                                leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null, tint = Color(0xFF00F2FF)) }
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isEditMode = !isEditMode }) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Close else Icons.Default.Edit,
                            contentDescription = "Toggle Edit Mode",
                            tint = Color(0xFF00F2FF)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF080808))
            )
        },
        floatingActionButton = {
            if (!isEditMode) {
                FloatingActionButton(
                    onClick = { navController.navigate("card_input") },
                    containerColor = Color(0xFF00F2FF),
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(16.dp)
                ) { Icon(Icons.Default.Add, contentDescription = "Add Card") }
            }
        },
        containerColor = Color(0xFF080808)
    ) { padding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(isEditMode) {
                    if (isEditMode) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            lazyListState.layoutInfo.visibleItemsInfo
                                .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
                                ?.let { draggingItemIndex = it.index }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.y
                            
                            val currentIndex = draggingItemIndex ?: return@detectDragGesturesAfterLongPress
                            val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                            val draggingItem = visibleItems.firstOrNull { it.index == currentIndex } ?: return@detectDragGesturesAfterLongPress
                            
                            // ドラッグ中のアイテムの中心物理座標
                            val draggingItemCenter = draggingItem.offset + (draggingItem.size / 2) + dragOffset
                            
                            // 入れ替わり対象のアイテムを判定
                            val targetItem = visibleItems.firstOrNull { item ->
                                item.index != currentIndex && 
                                draggingItemCenter.toInt() in item.offset..(item.offset + item.size)
                            }

                            if (targetItem != null) {
                                // 重要：入れ替わりによって発生するベース位置のズレを補正し、指の位置をキープする
                                val scrollAdjustment = draggingItem.offset - targetItem.offset
                                
                                val newList = listForDisplay.toMutableList()
                                Collections.swap(newList, currentIndex, targetItem.index)
                                
                                listForDisplay = newList
                                draggingItemIndex = targetItem.index
                                dragOffset += scrollAdjustment
                            }
                        },
                        onDragEnd = {
                            viewModel.updateCardOrder(listForDisplay)
                            draggingItemIndex = null
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            draggingItemIndex = null
                            dragOffset = 0f
                        }
                    )
                },
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(listForDisplay, key = { _, card -> card.id }) { index, card ->
                val isDragging = draggingItemIndex == index
                val elevation by animateDpAsState(if (isDragging) 16.dp else 0.dp, label = "elevation")
                
                CardItem(
                    card = card,
                    isEditMode = isEditMode,
                    onDelete = { viewModel.deleteCard(card) },
                    onClick = { if (draggingItemIndex == null && !isEditMode) selectedCard = card },
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            // 重要：ドラッグ中のアイテムのアニメーションを無効にして「ジャンプ」を防ぐ
                            placementSpec = if (isDragging) null else spring<IntOffset>()
                        )
                        .zIndex(if (isDragging) 100f else 1f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else 0f
                            shadowElevation = elevation.toPx()
                            scaleX = if (isDragging) 1.02f else 1f
                            scaleY = if (isDragging) 1.02f else 1f
                            alpha = if (isDragging) 0.95f else 1f
                        }
                        .shadow(elevation, RoundedCornerShape(16.dp))
                )
            }
        }

        selectedCard?.let { card ->
            CardDetailDialog(card = card, onDismiss = { selectedCard = null })
        }
    }
}

@Composable
fun CardItem(
    card: CardEntity,
    isEditMode: Boolean,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val last4 = if (card.cardNumber.length >= 4) card.cardNumber.takeLast(4) else card.cardNumber
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF121212))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(card.cardName.ifEmpty { "UNKNOWN" }, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default)
                Text("**** **** **** $last4", color = Color.Gray, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            }
            
            if (isEditMode) {
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Card", tint = Color.Red)
                }
            } else {
                Text(card.brand.uppercase(), color = Color(0xFF00F2FF), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CardDetailDialog(card: CardEntity, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121212),
        title = {
            Text("ENCRYPTED DETAILS", color = Color(0xFF00F2FF), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailItem(label = "IDENTIFIER", value = card.cardName)
                DetailItem(label = "CORE NUMBER", value = card.cardNumber.chunked(4).joinToString(" "))
                Row(modifier = Modifier.fillMaxWidth()) {
                    DetailItem(label = "EXP DATE", value = formatExpiry(card.expiryDate), modifier = Modifier.weight(1f))
                    DetailItem(label = "CVV", value = card.cvv, modifier = Modifier.weight(1f))
                }
                DetailItem(label = "BRAND", value = card.brand.uppercase())
                DetailItem(label = "ISSUER", value = card.issuer.ifEmpty { "UNKNOWN" }.uppercase())
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", color = Color(0xFF00F2FF)) }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun DetailItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        Text(value.ifEmpty { "---" }, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardInputScreen(navController: NavController, viewModel: CardViewModel) {
    var cardName by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var detectedBrand by remember { mutableStateOf("UNIDENTIFIED") }
    var expiryValue by remember { mutableStateOf(TextFieldValue("")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF080808))
            )
        },
        containerColor = Color(0xFF080808)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("NEW ENCRYPTION", style = MaterialTheme.typography.labelLarge, color = Color(0xFF00F2FF), letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(colors = listOf(Color(0xFF232323), Color(0xFF000000))))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .padding(28.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(detectedBrand.uppercase(), color = Color(0xFF00F2FF), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                        Box(modifier = Modifier.size(45.dp, 35.dp).clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(listOf(Color(0xFF00F2FF), Color(0xFF006B70)))))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (cardNumber.isEmpty()) "" else cardNumber.chunked(4).joinToString(" "),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("IDENTIFIER", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            Text(cardName.ifEmpty { "---" }, color = Color.White, fontFamily = FontFamily.Default, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("VALID THRU", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            Text(formatExpiry(expiryDate).ifEmpty { "--/--" }, color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                CyberInputField(value = cardName, onValueChange = { cardName = it }, label = "IDENTIFIER")
                CyberInputField(value = cardNumber, onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }
                    if (digits.length <= 16) {
                        cardNumber = digits
                        detectedBrand = detectCardBrand(digits)
                    }
                }, label = "CORE NUMBER", keyboardType = KeyboardType.Number)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CyberInputField(value = expiryValue, onValueChange = { newValue ->
                        val digits = newValue.text.filter { it.isDigit() }.take(4)
                        expiryValue = TextFieldValue(digits, TextRange(digits.length))
                        expiryDate = digits
                    }, label = "EXP DATE", modifier = Modifier.weight(1.2f), visualTransformation = ExpiryDateTransformation())
                    CyberInputField(value = cvv, onValueChange = { if (it.length <= 4) cvv = it.filter { it.isDigit() } }, label = "CVV", modifier = Modifier.weight(0.8f), keyboardType = KeyboardType.Number)
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = {
                    if (cardNumber.isNotEmpty()) {
                        viewModel.insertCard(cardName = cardName, cardNumber = cardNumber, expiryDate = expiryDate, cvv = cvv, brand = detectedBrand)
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FF), contentColor = Color.Black)
            ) { Text("SAVE", fontWeight = FontWeight.Black, letterSpacing = 2.sp) }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun CyberInputField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text) {
    Column(modifier = modifier) {
        Text(label, color = Color(0xFF00F2FF).copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        TextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF121212), focusedIndicatorColor = Color(0xFF00F2FF), unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = keyboardType), singleLine = true)
    }
}

@Composable
fun CyberInputField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, label: String, modifier: Modifier = Modifier, visualTransformation: VisualTransformation = VisualTransformation.None) {
    Column(modifier = modifier) {
        Text(label, color = Color(0xFF00F2FF).copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        TextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF121212), focusedIndicatorColor = Color(0xFF00F2FF), unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = visualTransformation, singleLine = true)
    }
}

fun formatExpiry(input: String): String = if (input.length >= 3) "${input.take(2)}/${input.substring(2)}" else input

fun detectCardBrand(digits: String): String = when {
    digits.startsWith("4") -> "Visa"
    digits.startsWith("5") -> "Mastercard"
    digits.length >= 2 && (digits.startsWith("34") || digits.startsWith("37")) -> "Amex"
    digits.length >= 2 && digits.startsWith("35") -> "JCB"
    digits.length >= 2 && (digits.startsWith("30") || digits.startsWith("36") || digits.startsWith("38")) -> "Diners Club"
    digits.startsWith("3") -> "IDENTIFYING..."
    else -> "CORE CARD"
}

class ExpiryDateTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        var out = ""
        for (i in text.text.indices) {
            out += text.text[i]
            if (i == 1) out += "/"
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = if (offset <= 1) offset else offset + 1
            override fun transformedToOriginal(offset: Int): Int = if (offset <= 2) offset else offset - 1
        }
        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}
