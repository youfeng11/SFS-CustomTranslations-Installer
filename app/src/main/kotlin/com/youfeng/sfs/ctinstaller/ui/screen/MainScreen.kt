package com.youfeng.sfs.ctinstaller.ui.screen

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.youfeng.sfs.ctinstaller.R
import com.youfeng.sfs.ctinstaller.core.Constants
import com.youfeng.sfs.ctinstaller.data.model.CTRadioOption
import com.youfeng.sfs.ctinstaller.data.model.RadioOption
import com.youfeng.sfs.ctinstaller.ui.component.AnnotatedLinkText
import com.youfeng.sfs.ctinstaller.ui.component.ErrorCard
import com.youfeng.sfs.ctinstaller.ui.component.OverflowMenu
import com.youfeng.sfs.ctinstaller.ui.component.RadioOptionItem
import com.youfeng.sfs.ctinstaller.ui.viewmodel.AppState
import com.youfeng.sfs.ctinstaller.ui.viewmodel.GrantedType
import com.youfeng.sfs.ctinstaller.ui.viewmodel.MainUiState
import com.youfeng.sfs.ctinstaller.ui.viewmodel.MainViewModel
import com.youfeng.sfs.ctinstaller.ui.viewmodel.UiEvent
import com.youfeng.sfs.ctinstaller.utils.openUrlInBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    onNavigatorToDetails: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    // 收集 UI 状态
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope() // 用于 Snackbar 和文件操作的协程作用域

    // 基础布局容器
    Surface(modifier = Modifier.fillMaxSize()) {
        MainLayout(
            onNavigatorToDetails = onNavigatorToDetails,
            onRequestPermissionsClicked = viewModel::onRequestPermissionsClicked,
            permissionRequestCheck = viewModel::permissionRequestCheck,
            uiState = uiState.appState, // 传递 AppState 给 StatusCard
            openSfs = viewModel::openSfs,
            onInstallButtonClick = viewModel::onInstallButtonClick,
            onSaveToButtonClick = viewModel::onSaveToButtonClick,
            filePicker = viewModel::filePicker,
            setRealOption = viewModel::setRealOption,
            sfsVersionName = viewModel.sfsVersionName,
            realOption = uiState.realOption,
            customTranslationsName = uiState.customTranslationsName,
            snackbarHostState = snackbarHostState,
            forGameVersion = uiState.forGameVersion,
            grantedType = uiState.grantedType,
            updateMessage = uiState.updateMessage,
            options = uiState.options,
            ctRadio = uiState.ctRadio
        )
    }

    // 处理生命周期事件，更新 ViewModel 状态
    LifecycleAwareHandler(
        onCreate = viewModel::activityOnCreate,
        onResume = viewModel::updateMainState,
        onStart = viewModel::updateStateFromRemote,
        onDestroy = viewModel::removeShizukuListener
    )

    // 处理一次性 UI 事件，例如显示 Snackbar，启动文件选择器等
    UiEventAwareHandler(
        viewModel,
        coroutineScope,
        snackbarHostState
    )

    if (uiState.showSettingsRedirectDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setGoToSettingsDialogVisible(false) },
            title = { Text(stringResource(R.string.settings_redirect_dialog_title)) },
            text = { Text(stringResource(R.string.settings_redirect_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setGoToSettingsDialogVisible(false)
                    viewModel.redirectToSystemSettings()
                }) { Text(stringResource(R.string.settings_redirect_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setGoToSettingsDialogVisible(false) }) {
                    Text(
                        stringResource(R.string.cancel)
                    )
                }
            }
        )
    }

    if (uiState.showInstallingDialog) {
        InstallingDialog(
            uiState,
            viewModel::setInstallingDialogVisible,
            viewModel::cancelCurrentTask
        )
    }
}

// 封装可复用的生命周期观察器
@Composable
fun LifecycleAwareHandler(
    onCreate: () -> Unit,
    onResume: () -> Unit,
    onStart: () -> Unit,
    onDestroy: () -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> onCreate()
                Lifecycle.Event.ON_RESUME -> onResume()
                Lifecycle.Event.ON_START -> onStart()
                Lifecycle.Event.ON_DESTROY -> onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
fun UiEventAwareHandler(
    viewModel: MainViewModel,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        viewModel.saveToUri(uri)
    }
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        val shouldShowRationale =
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        viewModel.onPermissionsChecked(it, shouldShowRationale)
    }
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) {
        viewModel.onFolderSelected(it)
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.RequestSafPermissions -> {
                    safLauncher.launch(event.sfsDataUri)
                }

                is UiEvent.PermissionRequestCheck -> {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }

                is UiEvent.RedirectToSystemSettings -> {
                    val intentSetting = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:${context.packageName}".toUri()
                    )
                    activity?.startActivity(intentSetting)
                }

                is UiEvent.SaveTo -> {
                    launcher.launch(event.fileName)
                }

                is UiEvent.ShowSnackbar -> {
                    coroutineScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        val result = if (event.actionLabel == null) {
                            snackbarHostState.showSnackbar(message = event.text)
                        } else {
                            snackbarHostState.showSnackbar(
                                message = event.text,
                                actionLabel = event.actionLabel,
                                withDismissAction = true,
                                duration = SnackbarDuration.Long
                            )
                        }
                        if (result == SnackbarResult.ActionPerformed) {
                            event.action?.invoke()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainLayout(
// 添加默认参数以便于预览
    onNavigatorToDetails: () -> Unit = {},
    onRequestPermissionsClicked: (selectedOption: GrantedType) -> Unit = {},
    permissionRequestCheck: () -> Unit = {},
    uiState: AppState = AppState.Uninstalled, // 更改为 AppState
    openSfs: () -> Unit = {},
    onInstallButtonClick: (realOption: Int) -> Unit = {},
    onSaveToButtonClick: (realOption: Int) -> Unit = {},
    filePicker: (uri: Uri?) -> Unit = {},
    setRealOption: (realOption: Int) -> Unit = {},
    sfsVersionName: String = "",
    realOption: Int = -1,
    customTranslationsName: String? = "",
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    grantedType: GrantedType = GrantedType.Saf,
    forGameVersion: String = "",
    updateMessage: String? = null,
    options: List<RadioOption> = emptyList(),
    ctRadio: List<CTRadioOption>? = null
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(
                    bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                        .asPaddingValues().calculateBottomPadding()
                )
            )
        },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                ),
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Black
                    )
                },
                actions = { OverflowMenu(onNavigatorToDetails) }, // 右上角菜单按钮
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                16.dp,
                16.dp,
                16.dp,
                16.dp + WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues()
                    .calculateBottomPadding()
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal
                    )
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),

            ) {
            item("states") {
                StatusCard(
                    appState = uiState, // 传递 AppState
                    onRequestPermissionsClicked = onRequestPermissionsClicked,
                    permissionRequestCheck = permissionRequestCheck,
                    openSfs = openSfs,
                    sfsVersionName = sfsVersionName,
                    grantedType = grantedType,
                    options = options
                )
            }
            if (updateMessage != null) {
                item("update") {
                    UpdateCard(updateMessage)
                }
            }
            item("install") {
                InstallCard(
                    onInstallButtonClick = onInstallButtonClick,
                    onSaveToButtonClick = onSaveToButtonClick,
                    enableInstallButton = uiState is AppState.Granted, // 根据 AppState 判断是否启用
                    filePicker = filePicker,
                    setRealOption = setRealOption,
                    forGameVersion = forGameVersion,
                    realOption = realOption,
                    customTranslationsName = customTranslationsName,
                    ctRadio = ctRadio
                )
            }
            item("donate") {
                DonateCard()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LazyItemScope.StatusCard(
    appState: AppState, // 更改为 AppState
    onRequestPermissionsClicked: (selectedOption: GrantedType) -> Unit,
    permissionRequestCheck: () -> Unit,
    openSfs: () -> Unit,
    sfsVersionName: String,
    grantedType: GrantedType,
    options: List<RadioOption>
) {
    var openDialog by remember { mutableStateOf(false) } // 仅用于 SAF 权限说明的对话框
    var selectedOption by remember { mutableStateOf(options.getOrNull(0)) }
    val color by animateColorAsState(
        targetValue = if (appState is AppState.Granted)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.errorContainer
    )
    CardWidget(
        title = {
            AnimatedContent(
                appState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) togetherWith fadeOut(
                        animationSpec = tween(
                            500
                        )
                    )
                }
            ) { appState ->
                Text(
                    when (appState) {
                        is AppState.Loading -> stringResource(R.string.loading)
                        is AppState.Uninstalled -> stringResource(R.string.state_type_uninstalled)
                        is AppState.NeverOpened -> stringResource(R.string.state_type_never_opened)
                        is AppState.Granted -> stringResource(R.string.state_type_granted)
                        else -> stringResource(R.string.state_type_ungranted)
                    }
                )
            }
        },
        icon = {
            AnimatedContent(
                appState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) togetherWith fadeOut(
                        animationSpec = tween(
                            500
                        )
                    )
                }
            ) { appState ->
                Icon(
                    when (appState) {
                        AppState.Granted -> Icons.Outlined.CheckCircle
                        AppState.Uninstalled -> Icons.Default.Block
                        else -> Icons.Default.Error
                    },
                    contentDescription = null
                )
            }
        },
        colors = CardDefaults.cardColors(
            containerColor = color
        ),
        iconColors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = if (appState is AppState.Granted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else MaterialTheme.colorScheme.onErrorContainer
        ),
        text = {
            AnimatedContent(
                appState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) togetherWith fadeOut(
                        animationSpec = tween(
                            500
                        )
                    )
                }
            ) { appState ->
                Text(
                    when (appState) {
                        is AppState.Loading -> stringResource(R.string.loading)
                        is AppState.Uninstalled -> stringResource(R.string.state_uninstalled_title)
                        is AppState.NeverOpened -> stringResource(R.string.state_neveropened_title)
                        is AppState.Ungranted -> stringResource(R.string.state_ungranted_title)
                        is AppState.Granted -> {
                            val type = when (grantedType) {
                                is GrantedType.Saf -> stringResource(R.string.permissions_saf)
                                is GrantedType.Old -> stringResource(R.string.permissions_old)
                                is GrantedType.Bug -> stringResource(R.string.permissions_exploit)
                                is GrantedType.Shizuku -> stringResource(R.string.permissions_shizuku)
                                is GrantedType.Su -> stringResource(R.string.permissions_root)
                            }
                            "$type | $sfsVersionName"
                        }
                    }
                )
            }
        },
        onClick = {
            when (appState) {
                is AppState.NeverOpened -> openSfs()
                is AppState.Ungranted -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        selectedOption = options[0]
                        openDialog = true
                    } else {
                        permissionRequestCheck()
                    }
                }

                else -> {}
            }
        }
    )

    val scope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState(isPersistent = true)
    if (openDialog) {
        AlertDialog(
            onDismissRequest = { openDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.permission_request_dialog_title))

                    TooltipBox(
                        positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Below,
                                8.dp
                            ),
                        tooltip = {
                            RichTooltip(
                                title = { Text(stringResource(R.string.permission_request_dialog_help_title)) },
                                caretShape = TooltipDefaults.caretShape()
                            ) { Text(stringResource(R.string.permission_request_dialog_text)) }
                        },
                        state = tooltipState,
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    tooltipState.show()
                                }
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = stringResource(R.string.permission_request_help)
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    Modifier
                        .selectableGroup()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (options[1].disableInfo != null)
                        ErrorCard(
                            text = {
                                AnnotatedLinkText(stringResource(R.string.all_options_unavailable_warning_text))
                            }
                        )
                    options.forEach { option ->
                        RadioOptionItem(
                            title = if (options[0].id == option.id && options[0].disableInfo == null) "${option.text}（推荐）" else option.text,
                            summary = option.disableInfo,
                            selected = option == selectedOption,
                            onClick = { selectedOption = option }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedOption?.disableInfo == null,
                    onClick = {
                        openDialog = false
                        selectedOption?.id?.let { onRequestPermissionsClicked(it) }
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { openDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun LazyItemScope.UpdateCard(updateMessage: String) {
    val context = LocalContext.current
    CardWidget(
        title = {
            Text(stringResource(R.string.card_item_update_title))
        },
        icon = {
            Icon(
                Icons.Default.Update,
                contentDescription = null
            )
        },
        onClick = {
            context.openUrlInBrowser(Constants.LATEST_RELEASE_URL)
        },
        text = {
            Text(context.getString(R.string.card_item_update_text, updateMessage))
        },
        iconColors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
private fun LazyItemScope.InstallCard(
    onInstallButtonClick: (realOption: Int) -> Unit,
    onSaveToButtonClick: (realOption: Int) -> Unit,
    filePicker: (uri: Uri?) -> Unit,
    setRealOption: (realOption: Int) -> Unit,
    realOption: Int,
    enableInstallButton: Boolean,
    forGameVersion: String,
    customTranslationsName: String?,
    ctRadio: List<CTRadioOption>?
) {
    var selectedOption by remember { mutableIntStateOf(realOption) }
    LaunchedEffect(realOption) {
        selectedOption = realOption
    }
    var openChooseDialog by remember { mutableStateOf(false) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        filePicker(uri)
        uri?.let {
            selectedOption = -2
        }
    }
    val context = LocalContext.current
    if (openChooseDialog) {
        AlertDialog(
            onDismissRequest = {
                openChooseDialog = false
                selectedOption = realOption
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.select_translation_dialog_title))
                    IconButton(onClick = {
                        context.openUrlInBrowser("https://github.com/youfeng11/SFS-CustomTranslations-Installer/blob/main/INTEGRATE.md")
                    }) {
                        Icon(
                            Icons.AutoMirrored.Default.OpenInNew,
                            contentDescription = null,
                        )
                    }
                }
            },
            text = {
                Column(
                    Modifier
                        .selectableGroup()
                        .verticalScroll(rememberScrollState())
                        .animateContentSize()
                ) {
                    RadioOptionItem(
                        title = stringResource(R.string.default_translation),
                        summary = "${stringResource(R.string.language_simplified_chinese)} | ${
                            stringResource(
                                R.string.default_text
                            )
                        }",
                        selected = -1 == selectedOption,
                        onClick = { selectedOption = -1 },
                        normal = true
                    )
                    RadioOptionItem(
                        title = stringResource(R.string.custom_translation_pack),
                        summary = if (customTranslationsName == null) stringResource(R.string.not_selected) else stringResource(
                            R.string.local_file,
                            customTranslationsName
                        ),
                        selected = -2 == selectedOption,
                        onClick = {
                            filePickerLauncher.launch(arrayOf("text/plain"))
                        },
                        normal = true
                    )
                    HorizontalDivider(modifier = Modifier.padding(12.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ctRadio?.forEachIndexed { index, option ->
                            RadioOptionItem(
                                title = option.title,
                                summary = option.text,
                                selected = index == selectedOption,
                                onClick = { selectedOption = index },
                                normal = true
                            )
                        } ?: run {
                            selectedOption = -1
                            setRealOption(-1)
                            Text(stringResource(R.string.loading_failed))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    setRealOption(selectedOption)
                    openChooseDialog = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    openChooseDialog = false
                    selectedOption = realOption
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    CardWidget({
        Text(stringResource(R.string.card_item_install_title))
    }, {
        Icon(
            Icons.Default.Archive,
            contentDescription = null
        )
    }) {
        val translationName = when (realOption) {
            -1 -> stringResource(R.string.default_translation)
            -2 -> context.getString(R.string.local_translation_name, customTranslationsName)
            else -> ctRadio?.getOrNull(realOption)?.title ?: context.getString(R.string.unknown)
        }
        Column {
            Text(context.getString(R.string.card_item_install_current_choice, translationName))
            if (realOption == -1)
                Text(
                    context.getString(
                        R.string.card_item_install_supported_version,
                        forGameVersion
                    )
                )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null
                )
                Spacer(Modifier.width(16.dp))
                Text(context.getString(R.string.card_item_install_tip))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(onClick = {
                        openChooseDialog = true
                    }) {
                        Icon(
                            Icons.Outlined.Settings,
                            null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.card_item_install_button_choice))
                    }
                }
                TextButton(
                    onClick = {
                        onSaveToButtonClick(realOption)
                    },
                    enabled = selectedOption != -2
                ) {
                    Text(stringResource(R.string.card_item_install_button_save))
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = {
                        onInstallButtonClick(realOption)
                    },
                    enabled = enableInstallButton
                ) {
                    Text(stringResource(R.string.card_item_install_button_install))
                }
            }
        }
    }
}

@Composable
private fun LazyItemScope.DonateCard() {
    val context = LocalContext.current
    CardWidget({
        Text(stringResource(R.string.card_item_donate_title))
    }, {
        Icon(
            Icons.Default.AttachMoney,
            contentDescription = null
        )
    }, text = {
        Text(context.getString(R.string.card_item_donate_text))
    }, onClick = {
        context.openUrlInBrowser("https://afdian.com/a/youfeng")
    })
}

@Composable
fun InstallingDialog(
    uiState: MainUiState,
    setInstallingDialogVisible: (Boolean) -> Unit,
    cancelCurrentTask: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (uiState.isInstallComplete) setInstallingDialogVisible(
                false
            )
        },
        title = {
            AnimatedContent(
                targetState = uiState.isInstallComplete,
                label = "DialogTitleAnimation" // 可选的标签，用于调试
            ) { isComplete ->
                Text(
                    if (isComplete) stringResource(R.string.installing_dialog_end) else stringResource(
                        R.string.installing_dialog_installing
                    )
                )
            }
        },
        text = {
            Column {
                if (!uiState.isInstallComplete)
                    LinearProgressIndicator(Modifier.wrapContentWidth())
                Box(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(uiState.installationProgressText, Modifier.animateContentSize())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                setInstallingDialogVisible(false)
                cancelCurrentTask() // 取消安装任务
            }) {
                Text(
                    if (uiState.isInstallComplete) stringResource(R.string.installing_dialog_button_done) else stringResource(
                        R.string.cancel
                    )
                )
            }
        }
    )
}

@Composable
private fun LazyItemScope.CardWidget(
    title: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.elevatedCardColors(),
    onClick: () -> Unit = {},
    iconColors: IconButtonColors = IconButtonDefaults.iconButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ),
    text: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    ElevatedCard(
        modifier = modifier.animateItem(),
        colors = colors,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(40.dp),
                    color = iconColors.containerColor,
                    contentColor = iconColors.contentColor
                ) {
                    Box(Modifier.padding(8.dp)) {
                        icon.invoke()
                    }
                }
                Column {
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        title.invoke()
                    }
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        AnimatedContent(targetState = text) {
                            it?.invoke()
                        }
                    }
                }
            }
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                content?.invoke()
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MainLayoutPreview() {
    MainLayout()
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MainLayoutPreview2() {
    MainLayout(uiState = AppState.Granted)
}
