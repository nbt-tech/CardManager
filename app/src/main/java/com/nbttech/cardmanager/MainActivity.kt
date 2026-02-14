package com.nbttech.cardmanager

import android.app.LocaleManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

val CardColors = listOf(
    0xFF1A1A1AL, // Dark Grey
    0xFF2D3436L, // City Lights
    0xFF0984E3L, // Electron Blue
    0xFF6C5CE7L, // Shy Moment
    0xFFB83227L, // Red
    0xFF006266L, // Turkish Aqua
    0xFF1B1464L, // 27 Club
    0xFF5758BBL, // Circumorbital Ring
    0xFF6F1E51L, // Magenta Purple
    0xFF2F3640L, // Electromagnetic
)

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
            var themeMode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }
            
            CardManagerTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                cardViewModel = viewModel()

                NavHost(navController = navController, startDestination = "card_list") {
                    composable("card_list") {
                        CardListScreen(
                            navController = navController,
                            viewModel = cardViewModel,
                            onExportJson = { exportJsonLauncher.launch("cards_backup.json") },
                            onExportCsv = { exportCsvLauncher.launch("cards_backup.csv") },
                            onImport = { importLauncher.launch(arrayOf("application/json", "text/csv", "text/comma-separated-values")) },
                            currentThemeMode = themeMode,
                            onThemeModeSelect = { themeMode = it }
                        )
                    }
                    composable("card_input") {
                        CardInputScreen(navController, cardViewModel)
                    }
                    composable(
                        route = "card_edit/{cardId}",
                        arguments = listOf(navArgument("cardId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val cardId = backStackEntry.arguments?.getInt("cardId") ?: -1
                        CardInputScreen(navController, cardViewModel, cardId)
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
            Toast.makeText(this@MainActivity, getString(R.string.exported_json), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this@MainActivity, getString(R.string.exported_csv), Toast.LENGTH_SHORT).show()
        }
    }

    private fun importData(uri: Uri) {
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: return@launch

                val cards = if (content.trim().startsWith("[")) {
                    val type = object : TypeToken<List<CardEntity>>() {}.type
                    Gson().fromJson<List<CardEntity>>(content, type)
                } else {
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
                    Toast.makeText(this@MainActivity, getString(R.string.imported_cards, cards.size), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun CardManagerTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            background = Color(0xFF0A0A0CL),
            surface = Color(0xFF16161AL),
            primary = Color(0xFF00E5FFL),
            secondary = Color(0xFF7000FFL),
            onPrimary = Color.Black,
            onSurface = Color(0xFFE1E1E6L),
            onBackground = Color.White,
            surfaceVariant = Color(0xFF202024L),
            onSurfaceVariant = Color(0xFFA8A8B3L)
        )
    } else {
        lightColorScheme(
            background = Color(0xFFF0F2F5L),
            surface = Color.White,
            primary = Color(0xFF0066FFL),
            secondary = Color(0xFF6200EEL),
            onPrimary = Color.White,
            onSurface = Color(0xFF1C1C1EL),
            onBackground = Color(0xFF1C1C1EL),
            surfaceVariant = Color(0xFFE5E5EAL),
            onSurfaceVariant = Color(0xFF8E8E93L)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
    onImport: () -> Unit,
    currentThemeMode: ThemeMode,
    onThemeModeSelect: (ThemeMode) -> Unit
) {
    val dbCards by viewModel.allCards.collectAsState(initial = emptyList())
    var listForDisplay by remember { mutableStateOf(emptyList<CardEntity>()) }
    
    LaunchedEffect(dbCards) {
        listForDisplay = dbCards
    }

    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var selectedCard by remember { mutableStateOf<CardEntity?>(null) }
    var isEditMode by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showExportOptions by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    
    val lazyListState = rememberLazyListState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.my_vault).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            letterSpacing = 6.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu_desc), tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .width(220.dp)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_data), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium) },
                                onClick = { 
                                    showMenu = false
                                    onImport() 
                                },
                                leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium) },
                                onClick = { 
                                    showMenu = false
                                    showExportOptions = true 
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                                trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.theme), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium) },
                                onClick = { showMenu = false; showThemeMenu = true },
                                leadingIcon = { 
                                    val icon = when (currentThemeMode) {
                                        ThemeMode.DARK -> Icons.Default.DarkMode
                                        ThemeMode.LIGHT -> Icons.Default.LightMode
                                        ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                                    }
                                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) 
                                },
                                trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.language), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium) },
                                onClick = { showMenu = false; showLanguageMenu = true },
                                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                                trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        DropdownMenu(
                            expanded = showExportOptions,
                            onDismissRequest = { showExportOptions = false },
                            modifier = Modifier
                                .width(180.dp)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_json), color = MaterialTheme.colorScheme.onSurface) },
                                onClick = { showExportOptions = false; onExportJson() },
                                leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_csv), color = MaterialTheme.colorScheme.onSurface) },
                                onClick = { showExportOptions = false; onExportCsv() },
                                leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)) }
                            )
                        }
                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false },
                            modifier = Modifier
                                .width(200.dp)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.system_mode), color = MaterialTheme.colorScheme.onSurface) },
                                onClick = { 
                                    showThemeMenu = false
                                    onThemeModeSelect(ThemeMode.SYSTEM)
                                },
                                leadingIcon = { Icon(Icons.Default.SettingsBrightness, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = { if (currentThemeMode == ThemeMode.SYSTEM) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dark_mode), color = MaterialTheme.colorScheme.onSurface) },
                                onClick = { 
                                    showThemeMenu = false
                                    onThemeModeSelect(ThemeMode.DARK)
                                },
                                leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = { if (currentThemeMode == ThemeMode.DARK) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.light_mode), color = MaterialTheme.colorScheme.onSurface) },
                                onClick = { 
                                    showThemeMenu = false
                                    onThemeModeSelect(ThemeMode.LIGHT)
                                },
                                leadingIcon = { Icon(Icons.Default.LightMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = { if (currentThemeMode == ThemeMode.LIGHT) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                        }
                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false },
                            modifier = Modifier
                                .width(180.dp)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.japanese), color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showLanguageMenu = false
                                    changeLanguage(context, "ja")
                                },
                                leadingIcon = { Text("🇯🇵", fontSize = 16.sp) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.english), color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showLanguageMenu = false
                                    changeLanguage(context, "en")
                                },
                                leadingIcon = { Text("🇺🇸", fontSize = 16.sp) }
                            )
                        }
                    }
                },
                actions = {
                    val isCurrentlyDark = when (currentThemeMode) {
                        ThemeMode.LIGHT -> false
                        ThemeMode.DARK -> true
                        ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    }
                    IconButton(
                        onClick = { isEditMode = !isEditMode },
                        modifier = if (isEditMode) {
                            Modifier.background(
                                color = if (isCurrentlyDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) 
                                        else Color(0xFF1B5E20L).copy(alpha = 0.1f),
                                shape = CircleShape
                            )
                        } else Modifier
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_mode_toggle),
                            tint = if (isEditMode) {
                                if (isCurrentlyDark) MaterialTheme.colorScheme.primary else Color(0xFF1B5E20L)
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
            )
        },
        floatingActionButton = {
            if (!isEditMode) {
                FloatingActionButton(
                    onClick = { navController.navigate("card_input") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(20.dp)
                ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_card), modifier = Modifier.size(32.dp)) }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (listForDisplay.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CreditCardOff,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.vault_empty).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        letterSpacing = 2.sp
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
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
                                    val draggingItemCenter = draggingItem.offset + (draggingItem.size / 2) + dragOffset
                                    val targetItem = visibleItems.firstOrNull { item ->
                                        item.index != currentIndex && 
                                        draggingItemCenter.toInt() in item.offset..(item.offset + item.size)
                                    }
                                    if (targetItem != null) {
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
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    itemsIndexed(listForDisplay, key = { _, card -> card.id }) { index, card ->
                        val isDragging = draggingItemIndex == index
                        val elevation by animateDpAsState(if (isDragging) 24.dp else 0.dp)
                        
                        CardItem(
                            card = card,
                            isEditMode = isEditMode,
                            onDelete = { viewModel.deleteCard(card) },
                            onClick = { if (draggingItemIndex == null && !isEditMode) selectedCard = card },
                            modifier = Modifier
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffset else 0f
                                    scaleX = if (isDragging) 1.02f else 1f
                                    scaleY = if (isDragging) 1.02f else 1f
                                    alpha = if (isDragging) 0.8f else 1f
                                }
                                .zIndex(if (isDragging) 1f else 0f)
                        )
                    }
                }
            }
        }

        selectedCard?.let { card ->
            CardDetailDialog(
                card = card,
                onDismiss = { selectedCard = null },
                onEdit = {
                    selectedCard = null
                    navController.navigate("card_edit/${card.id}")
                }
            )
        }
    }
}

fun changeLanguage(context: android.content.Context, languageCode: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(languageCode)
    } else {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageCode))
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
    val baseColor = Color(card.color)
    val isDark = isSystemInDarkTheme()
    val cardGradient = listOf(baseColor, baseColor.copy(alpha = 0.8f))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(cardGradient))
            .clickable { onClick() }
            .then(
                if (isDark) Modifier.border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .padding(20.dp)
                .size(35.dp, 25.dp)
                .align(Alignment.TopEnd)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
        )

        Text(
            card.brand.uppercase(),
            modifier = Modifier.padding(20.dp).align(Alignment.TopStart),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        )

        Column(
            modifier = Modifier.padding(20.dp).align(Alignment.BottomStart)
        ) {
            Text(
                card.cardName.ifEmpty { "UNKNOWN" }.uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                letterSpacing = 1.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "•••• •••• •••• $last4",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace, 
                    fontSize = 20.sp,
                    letterSpacing = 1.sp
                ),
                maxLines = 1
            )
        }

        if (isEditMode) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.Center)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun CardDetailDialog(card: CardEntity, onDismiss: () -> Unit, onEdit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.encrypted_details), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_card), tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailItem(label = stringResource(R.string.identifier), value = card.cardName)
                DetailItem(label = stringResource(R.string.core_number), value = card.cardNumber.chunked(4).joinToString(" "), copyValue = card.cardNumber)
                Row(modifier = Modifier.fillMaxWidth()) {
                    DetailItem(label = stringResource(R.string.exp_date), value = formatExpiry(card.expiryDate), modifier = Modifier.weight(1f), copyValue = card.expiryDate)
                    DetailItem(label = stringResource(R.string.cvv), value = card.cvv, modifier = Modifier.weight(1f), copyValue = card.cvv)
                }
                DetailItem(label = stringResource(R.string.brand), value = card.brand.uppercase())
                DetailItem(label = stringResource(R.string.issuer), value = card.issuer.ifEmpty { stringResource(R.string.unknown) }.uppercase())
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = MaterialTheme.colorScheme.primary) }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun DetailItem(label: String, value: String, modifier: Modifier = Modifier, copyValue: String? = null) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth().clickable(enabled = copyValue != null) {
        copyValue?.let {
            clipboardManager.setText(AnnotatedString(it))
            Toast.makeText(context, context.getString(R.string.copied_toast, label), Toast.LENGTH_SHORT).show()
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            if (copyValue != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
            }
        }
        Text(value.ifEmpty { "---" }, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardInputScreen(navController: NavController, viewModel: CardViewModel, cardId: Int = -1) {
    var cardName by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var detectedBrand by remember { mutableStateOf("") }
    var expiryValue by remember { mutableStateOf(TextFieldValue("")) }
    var selectedCardColor by remember { mutableLongStateOf(CardColors[0]) }
    val isEditMode = cardId != -1
    var existingCard by remember { mutableStateOf<CardEntity?>(null) }
    val identifyingText = stringResource(R.string.identifying)
    val coreCardText = stringResource(R.string.core_card)

    LaunchedEffect(cardId) {
        if (isEditMode) {
            val card = viewModel.getCardById(cardId)
            if (card != null) {
                existingCard = card
                cardName = card.cardName
                cardNumber = card.cardNumber
                expiryDate = card.expiryDate
                cvv = card.cvv
                detectedBrand = card.brand
                expiryValue = TextFieldValue(card.expiryDate, TextRange(card.expiryDate.length))
                selectedCardColor = card.color
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(8.dp))
            Text((if (isEditMode) stringResource(R.string.update_encryption) else stringResource(R.string.new_encryption)).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(selectedCardColor), Color(selectedCardColor).copy(alpha = 0.8f)))).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)).padding(20.dp)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text((if (detectedBrand.isEmpty()) coreCardText else detectedBrand).uppercase(), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, maxLines = 1)
                        Box(modifier = Modifier.size(40.dp, 30.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.1f)).border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp)))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (cardNumber.isEmpty()) "" else cardNumber.chunked(4).joinToString(" "), 
                        color = Color.White, 
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            letterSpacing = 1.sp
                        ), 
                        fontFamily = FontFamily.Monospace, 
                        maxLines = 1, 
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.identifier).uppercase(), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                            Text(cardName.ifEmpty { "---" }.uppercase(), color = Color.White, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.exp_date).uppercase(), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                            Text(formatExpiry(expiryDate).ifEmpty { "--/--" }, color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.card_color).uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold), modifier = Modifier.align(Alignment.Start).padding(start = 4.dp, bottom = 12.dp) )
            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(CardColors) { color ->
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(color)).border(width = if (selectedCardColor == color) 3.dp else 0.dp, color = if (selectedCardColor == color) MaterialTheme.colorScheme.primary else Color.Transparent, shape = CircleShape).clickable { selectedCardColor = color })
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CyberInputField(value = cardName, onValueChange = { cardName = it }, label = stringResource(R.string.identifier))
                CyberInputField(value = cardNumber, onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }
                    if (digits.length <= 16) {
                        cardNumber = digits
                        detectedBrand = detectCardBrand(digits, identifyingText, coreCardText)
                    }
                }, label = stringResource(R.string.core_number), keyboardType = KeyboardType.Number)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CyberInputField(value = expiryValue, onValueChange = { newValue ->
                        val digits = newValue.text.filter { it.isDigit() }.take(4)
                        expiryValue = TextFieldValue(digits, TextRange(digits.length))
                        expiryDate = digits
                    }, label = stringResource(R.string.exp_date), modifier = Modifier.weight(1.2f), keyboardType = KeyboardType.Number, visualTransformation = ExpiryDateTransformation())
                    CyberInputField(value = cvv, onValueChange = { if (it.length <= 4) cvv = it.filter { it.isDigit() } }, label = stringResource(R.string.cvv), modifier = Modifier.weight(0.8f), keyboardType = KeyboardType.Number)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = {
                if (cardNumber.isNotEmpty()) {
                    if (isEditMode && existingCard != null) {
                        viewModel.updateCard(existingCard!!.copy(cardName = cardName, cardNumber = cardNumber, expiryDate = expiryDate, cvv = cvv, brand = if (detectedBrand.isEmpty() || detectedBrand == identifyingText) coreCardText else detectedBrand, color = selectedCardColor))
                    } else {
                        viewModel.insertCard(cardName = cardName, cardNumber = cardNumber, expiryDate = expiryDate, cvv = cvv, brand = if (detectedBrand.isEmpty() || detectedBrand == identifyingText) coreCardText else detectedBrand, color = selectedCardColor)
                    }
                    navController.popBackStack()
                }
            }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Text((if (isEditMode) stringResource(R.string.update) else stringResource(R.string.save)).uppercase(), fontWeight = FontWeight.Black, letterSpacing = 2.sp) }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun CyberInputField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text) {
    Column(modifier = modifier) {
        Text(label.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = keyboardType), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
    }
}

@Composable
fun CyberInputField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, label: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text, visualTransformation: VisualTransformation = VisualTransformation.None) {
    Column(modifier = modifier) {
        Text(label.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = keyboardType), visualTransformation = visualTransformation, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
    }
}

fun formatExpiry(input: String): String = if (input.length >= 3) "${input.take(2)}/${input.substring(2)}" else input

fun detectCardBrand(digits: String, identifyingText: String, defaultText: String): String = when {
    digits.startsWith("4") -> "Visa"
    digits.startsWith("5") -> "Mastercard"
    digits.length >= 2 && (digits.startsWith("34") || digits.startsWith("37")) -> "Amex"
    digits.length >= 2 && digits.startsWith("35") -> "JCB"
    digits.length >= 2 && (digits.startsWith("30") || digits.startsWith("36") || digits.startsWith("38")) -> "Diners Club"
    digits.startsWith("3") -> identifyingText
    else -> defaultText
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
