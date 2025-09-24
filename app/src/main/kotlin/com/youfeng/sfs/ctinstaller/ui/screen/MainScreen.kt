package com.youfeng.sfs.ctinstaller.ui.screen

import android.Manifest
import androidx.activity.compose.LocalActivity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
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
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.anggrayudi.storage.permission.ActivityPermissionRequest
import com.youfeng.sfs.ctinstaller.R
import com.youfeng.sfs.ctinstaller.data.model.RadioOption
import com.youfeng.sfs.ctinstaller.ui.component.OverflowMenu
import com.youfeng.sfs.ctinstaller.ui.component.RadioOptionItem
import com.youfeng.sfs.ctinstaller.ui.viewmodel.AppState
import com.youfeng.sfs.ctinstaller.ui.viewmodel.GrantedType
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
            sfsVersionName = viewModel.sfsVersionName,
            snackbarHostState = snackbarHostState,
            forGameVersion = uiState.forGameVersion,
            grantedType = uiState.grantedType,
            options = uiState.options
        )
    }

    // 处理生命周期事件，更新 ViewModel 状态
    LifecycleAwareHandler(
        onCreate = viewModel::addShizukuListener,
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

    if (uiState.showGoToSettingsDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setGoToSettingsDialogVisible(false) },
            title = { Text("要前往设置授权吗？") },
            text = { Text("由于您不再允许 SFS汉化安装器 请求 存储 权限申请。\n而安装汉化需要 存储 权限，因此您必须在授予 SFS汉化安装器 的 存储 权限后才能安装汉化。\n\n请在接下来的页面中，进入权限页面，并授予 SFS汉化安装器 存储 权限，完成后返回") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setGoToSettingsDialogVisible(false)
                    viewModel.redirectToSystemSettings()
                }) { Text("前往授权") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setGoToSettingsDialogVisible(false) }) {
                    Text(
                        "取消"
                    )
                }
            }
        )
    }

    if (uiState.showInstallingDialog) {
        AlertDialog(
            onDismissRequest = {
                if (uiState.isInstallComplete) viewModel.setInstallingDialogVisible(
                    false
                )
            },
            title = {
                AnimatedContent(
                    targetState = uiState.isInstallComplete,
                    label = "DialogTitleAnimation" // 可选的标签，用于调试
                ) { isComplete ->
                    Text(if (isComplete) "安装结束" else "安装汉化中")
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .animateContentSize()
                ) {
                    Text(uiState.installationProgressText)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setInstallingDialogVisible(false)
                    viewModel.cancelCurrentTask() // 取消安装任务
                }) { Text(if (uiState.isInstallComplete) "完成" else "取消") }
            }
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
    var text by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                        writer?.write(text)
                    }
                    viewModel.showSnackbar("汉化已保存")
                    text = ""
                } catch (e: Exception) {
                    e.printStackTrace()
                    viewModel.showSnackbar("保存失败: ${e.message}")
                    text = ""
                }
            }
        }
    }
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        val shouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
                    safLauncher.launch(viewModel.sfsDataUri)
                }

                is UiEvent.PermissionRequestCheck -> {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }

                is UiEvent.SaveTo -> {
                    text = event.content
                    launcher.launch("简体中文.txt")
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
private fun MainLayout(// 添加默认参数以便于预览
    onNavigatorToDetails: () -> Unit = {},
    onRequestPermissionsClicked: (selectedOption: GrantedType) -> Unit = {},
    permissionRequestCheck: () -> Unit = {},
    uiState: AppState = AppState.Uninstalled, // 更改为 AppState
    openSfs: () -> Unit = {},
    onInstallButtonClick: () -> Unit = {},
    onSaveToButtonClick: () -> Unit = {},
    sfsVersionName: String = "",
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    grantedType: GrantedType = GrantedType.Saf,
    forGameVersion: String = "",
    options: List<RadioOption> = emptyList(),
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
                title = { Text(stringResource(R.string.app_name)) },
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
            if (false) {
                item("update") {
                    UpdateCard()
                }
            }
            item("install") {
                InstallCard(
                    onInstallButtonClick = onInstallButtonClick,
                    onSaveToButtonClick = onSaveToButtonClick,
                    enableInstallButton = uiState is AppState.Granted, // 根据 AppState 判断是否启用
                    forGameVersion = forGameVersion
                )
            }
            item("donate") {
                DonateCard()
            }
        }
    }
}

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
    var selectedOption by remember { mutableStateOf(options[0]) }
    val color by animateColorAsState(
        targetValue = if (appState is AppState.Granted)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.errorContainer
    )
    CardWidget(
        title = {
            Text(
                when (appState) {
                    is AppState.Uninstalled -> "未安装"
                    is AppState.NeverOpened -> "未创建数据目录"
                    is AppState.Granted -> "已授权"
                    else -> "未授权"
                }
            )
        },
        icon = {
            Icon(
                when (appState) {
                    AppState.Granted -> Icons.Outlined.CheckCircle
                    AppState.Uninstalled -> Icons.Default.Block
                    else -> Icons.Default.Error
                },
                contentDescription = null
            )
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
            Text(
                when (appState) {
                    is AppState.Uninstalled -> "你未安装SFS，因此无法安装汉化"
                    is AppState.NeverOpened -> "点击此处打开SFS"
                    is AppState.Ungranted -> "点击此处前往授权"
                    is AppState.Granted -> {
                        val type = when (grantedType) {
                            is GrantedType.Saf -> "SAF授权"
                            is GrantedType.Old -> "存储权限授权"
                            is GrantedType.Bug -> "漏洞授权"
                            is GrantedType.Shizuku -> "Shizuku/Sui授权"
                            is GrantedType.Su -> "ROOT授权"
                        }
                        "$type | $sfsVersionName"
                    }
                }
            )
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

    if (openDialog) {
        AlertDialog(
            onDismissRequest = { openDialog = false },
            title = { Text("请选择授权方式") },
            text = {
                Column(
                    Modifier
                        .selectableGroup()
                        .verticalScroll(rememberScrollState())
                ) { // selectableGroup 用于辅助无障碍功能
                    Text("SFS的自定义语言文件夹位于其 Android/data 下的数据目录内。但是，从 Android 11 开始，系统为保障用户隐私而限制第三方应用使其不可访问 Android/data 及其子目录。\n因此，您必须通过以下方式授权 SFS汉化安装器 后才能安装汉化。")
                    HorizontalDivider(
                        modifier = Modifier.padding(
                            top = 10.dp,
                            start = 4.dp,
                            end = 4.dp
                        )
                    )
                    options.forEach { option ->
                        RadioOptionItem(
                            title = if (options[0].id == option.id) "${option.text}（推荐）" else option.text,
                            summary = option.disableInfo,
                            selected = option == selectedOption,
                            onClick = { selectedOption = option }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    openDialog = false
                    onRequestPermissionsClicked(selectedOption.id)
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { openDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun LazyItemScope.UpdateCard() {
    CardWidget(
        {
            Text("有新的版本可更新！")
        },
        {
            Icon(
                Icons.Default.Update,
                contentDescription = null
            )
        },
        text = {
            Text("新版本：9.9.9 (99)")
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
    onInstallButtonClick: () -> Unit,
    onSaveToButtonClick: () -> Unit,
    enableInstallButton: Boolean,
    forGameVersion: String

) {
    var openChooseDialog by remember { mutableStateOf(false) }
    if (openChooseDialog) {
        AlertDialog(
            onDismissRequest = { openChooseDialog = false },
            title = { Text("选择要安装的汉化") },
            text = {
                Text("未完成功能\n开发者还在努力制作中 ⚈₃⚈꧞")
            },
            confirmButton = {
                TextButton(onClick = { openChooseDialog = false }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { openChooseDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    CardWidget({
        Text("安装汉化")
    }, {
        Icon(
            Icons.Default.Archive,
            contentDescription = null
        )
    }) {
        Column {
            Text("当前选择：简体中文")
            Text("适用版本：$forGameVersion")
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null
                )
                Spacer(Modifier.width(16.dp))
                Text("请先确保您已正确完成授权等操作，且设备能够正常连接至互联网。")
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
                        Text("选择汉化")
                    }
                }
                TextButton(onClick = {
                    onSaveToButtonClick()
                }) {
                    Text("保存到")
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = {
                        onInstallButtonClick()
                    },
                    enabled = enableInstallButton
                ) {
                    Text("安装")
                }
            }
        }
    }
}

@Composable
private fun LazyItemScope.DonateCard() {
    val context = LocalContext.current
    CardWidget({
        Text("支持开发")
    }, {
        Icon(
            Icons.Default.AttachMoney,
            contentDescription = null
        )
    }, text = {
        Text("SFS汉化安装器 将保持免费开源，向开发者捐赠以表示支持。")
    }, onClick = {
        context.openUrlInBrowser("https://github.com/youfeng11/SFS-CustomTranslations-Installer#%E6%8D%90%E8%B5%A0")
    })
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
                    ProvideTextStyle(MaterialTheme.typography.titleMedium) {
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
