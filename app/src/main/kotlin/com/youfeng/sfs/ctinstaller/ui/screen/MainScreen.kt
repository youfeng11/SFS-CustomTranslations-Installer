package com.youfeng.sfs.ctinstaller.ui.screen

import android.os.Build
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.anggrayudi.storage.SimpleStorageHelper
import com.anggrayudi.storage.callback.SingleFileConflictCallback
import com.anggrayudi.storage.file.copyFileTo
import com.anggrayudi.storage.media.FileDescription
import com.anggrayudi.storage.permission.ActivityPermissionRequest
import com.anggrayudi.storage.result.SingleFileResult
import com.youfeng.sfs.ctinstaller.R
import com.youfeng.sfs.ctinstaller.core.Constants
import com.youfeng.sfs.ctinstaller.ui.component.OverflowMenu
import com.youfeng.sfs.ctinstaller.ui.viewmodel.MainState
import com.youfeng.sfs.ctinstaller.ui.viewmodel.MainViewModel
import com.youfeng.sfs.ctinstaller.ui.viewmodel.UiEvent
import com.youfeng.sfs.ctinstaller.utils.openUrlInBrowser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

// 是不是乱了点🤔

val job = SupervisorJob()
val ioScope = CoroutineScope(Dispatchers.IO + job)
val uiScope = CoroutineScope(Dispatchers.Main + job)

@Composable
fun MainScreen(
    onNavigatorToDetails: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
    storageHelper: SimpleStorageHelper,
    permissionRequest: ActivityPermissionRequest
) {
    val uiState by viewModel.state.collectAsState()
    val isInstallComplete by viewModel.isInstallComplete.collectAsState()
    val installationProgressText by viewModel.installationProgressText.collectAsState()
    val openErrorDialog by viewModel.openErrorDialog.collectAsState()
    val openInstallingDialog by viewModel.openInstallingDialog.collectAsState()
    val openPermissionDeniedDialog by viewModel.openPermissionDeniedDialog.collectAsState()
    val openGoToSettingsDialog by viewModel.openGoToSettingsDialog.collectAsState()

    // 基础布局容器
    Surface(modifier = Modifier.fillMaxSize()) {
        MainLayout(
            onNavigatorToDetails = onNavigatorToDetails,
            onRequestPermissionsClicked = viewModel::onRequestPermissionsClicked,
            uiState = uiState,
            openSfs = viewModel::openSfs,
            onInstallButtonClick = viewModel::onInstallButtonClick,
            sfsVersionName = viewModel.sfsVersionName
        ) {
            permissionRequest.check()
        }
    }

    LifecycleAwareHandler(
        viewModel::updateMainState
    )

    UiEventAwareHandler(viewModel, storageHelper)

    if (openErrorDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setErrorDialogVisibility(false) },
            title = { Text("选择了错误的文件夹") },
            text = {
                Text("您似乎未正确选择相应的文件夹！\n在授权页面请勿进行其他操作，直接点击底部的“使用此文件夹”按钮！\n如果你无法完成授权，请尝试前往设置使用高级权限授权！")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setErrorDialogVisibility(false)
                    viewModel.onRequestPermissionsClicked()
                }) {
                    Text("重试")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setErrorDialogVisibility(false) }) {
                    Text("取消")
                }
            }
        )
    }

    if (openPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setPermissionDeniedDialogVisible(false) },
            title = { Text("您取消了授权") },
            text = {
                Text("您似乎拒绝了 存储 权限申请。\n安装汉化需要 存储 权限，您必须在授予 SFS汉化安装器 的 存储 权限后才能安装汉化。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setPermissionDeniedDialogVisible(false)
                    permissionRequest.check()
                }) {
                    Text("前往授权")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setPermissionDeniedDialogVisible(false) }) {
                    Text("取消")
                }
            }
        )
    }

    if (openGoToSettingsDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setGoToSettingsDialogVisible(false) },
            title = { Text("要前往设置授权吗？") },
            text = {
                Text("由于您不再允许 SFS汉化安装器 请求 存储 权限申请。\n而安装汉化需要 存储 权限，因此您必须在授予 SFS汉化安装器 的 存储 权限后才能安装汉化。\n\n请在接下来的页面中，进入权限页面，并授予 SFS汉化安装器 存储 权限，完成后返")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setGoToSettingsDialogVisible(false)
                    viewModel.redirectToSystemSettings()
                }) {
                    Text("前往授权")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setGoToSettingsDialogVisible(false) }) {
                    Text("取消")
                }
            }
        )
    }

    if (openInstallingDialog) {
        AlertDialog(
            onDismissRequest = { if (isInstallComplete) viewModel.setInstallingDialogVisible(false) },
            title = { Text(if (isInstallComplete) "安装结束" else "安装汉化中") },
            text = {
                Text(installationProgressText)//, modifier = Modifier.animateContentSize())
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setInstallingDialogVisible(false)
                    ioScope.coroutineContext.cancelChildren()
                    viewModel.cancelInstallation()
                }) {
                    Text(if (isInstallComplete) "完成" else "取消")
                }
            }
        )
    }
}

// 封装可复用的生命周期观察器
@Composable
fun LifecycleAwareHandler(
    onStart: () -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) onStart()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
fun UiEventAwareHandler(
    viewModel: MainViewModel,
    storageHelper: SimpleStorageHelper
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.RequestSafPermissions -> {
                    storageHelper.requestStorageAccess(
                        initialPath = event.fileFullPath,
                        expectedBasePath = event.expectedBasePath
                    )
                }

                is UiEvent.Install -> ioScope.launch {
                    try {
                        event.file.copyFileTo(
                            context,
                            event.targetFolder,
                            fileDescription = FileDescription("简体中文.txt"),
                            onConflict = object :
                                SingleFileConflictCallback<DocumentFile>(uiScope) {
                                override fun onFileConflict(
                                    destinationFile: DocumentFile,
                                    action: FileConflictAction
                                ) {
                                    val resolution =
                                        ConflictResolution.REPLACE
                                    action.confirmResolution(resolution)

                                }
                            }
                        ).onCompletion {
                            if (it is CancellationException) {
                                viewModel.updateInstallationProgressText("汉化安装中止")
                            }
                        }.collect {
                            viewModel.updateInstallationProgressText(
                                when (it) {
                                    is SingleFileResult.Validating -> "验证中..."
                                    is SingleFileResult.Preparing -> "准备中..."
                                    is SingleFileResult.CountingFiles -> "正在计算文件..."
                                    is SingleFileResult.DeletingConflictedFile -> "正在删除冲突的文件..."

                                    is SingleFileResult.Starting -> "开始中..."
                                    is SingleFileResult.InProgress -> "进度：${it.progress.toInt()}%"
                                    is SingleFileResult.Completed -> "复制成功"

                                    is SingleFileResult.Error -> "发生错误：${it.errorCode.name}"
                                }
                            )
                        }
                    } catch (e: Exception) {
                        val err = e.message ?: e
                        viewModel.updateInstallationProgressText("错误：$err")
                    }
                    viewModel.updateInstallationProgressText("安装结束", true)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainLayout(// 添加默认参数以便于预览
    onNavigatorToDetails: () -> Unit = {},
    onRequestPermissionsClicked: () -> Unit = {},
    uiState: MainState = MainState.Uninstalled,
    openSfs: () -> Unit = {},
    onInstallButtonClick: () -> Unit = {},
    sfsVersionName: String = "",
    permissionRequestCheck: () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                windowInsets =
                    WindowInsets.safeDrawing.only(
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
                    uiState = uiState,
                    onRequestPermissionsClicked = onRequestPermissionsClicked,
                    openSfs = openSfs,
                    sfsVersionName = sfsVersionName,
                    permissionRequestCheck = permissionRequestCheck
                )
            }
            if (false) {
                item("update") {
                    UpdateCard()
                }
            }
            item("install") {
                InstallCard(
                    btnInstallOnClick = onInstallButtonClick,
                    enableInstallButton = uiState is MainState.Granted
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
    uiState: MainState,
    onRequestPermissionsClicked: () -> Unit,
    openSfs: () -> Unit,
    sfsVersionName: String,
    permissionRequestCheck: () -> Unit
) {
    var openDialog by remember { mutableStateOf(false) }
    CardWidget(
        title = {
            Text(
                when (uiState) {
                    is MainState.Uninstalled -> "未安装"
                    is MainState.NeverOpened -> "未创建数据目录"
                    is MainState.Granted -> "已授权"
                    else -> "未授权"
                }
            )
        },
        icon = {
            Icon(
                when (uiState) {
                    MainState.Granted -> Icons.Outlined.CheckCircle
                    MainState.Uninstalled -> Icons.Default.Block
                    else -> Icons.Default.Error
                },
                contentDescription = null
            )
        },
        colors = CardDefaults.cardColors(
            containerColor = if (uiState is MainState.Granted) {
                MaterialTheme.colorScheme.primaryContainer
            } else MaterialTheme.colorScheme.errorContainer
        ),
        iconColors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = if (uiState is MainState.Granted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else MaterialTheme.colorScheme.onErrorContainer
        ),
        text = {
            Text(
                when (uiState) {
                    is MainState.Uninstalled -> "你未安装SFS，因此无法安装汉化"
                    is MainState.NeverOpened -> "点击此处打开SFS"
                    is MainState.Ungranted -> "点击此处前往授权"
                    is MainState.Granted -> "当前游戏版本：${sfsVersionName}"
                }
            )
        },
        onClick = {
            when (uiState) {
                is MainState.NeverOpened -> openSfs()
                is MainState.Ungranted -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
            title = { Text("确定前往授权？") },
            text = {
                Text("SFS的自定义语言文件夹位于其 Android/data 下的数据目录内。\n但是，从 Android 11 开始，系统为保障用户隐私而限制第三方应用，使其不可访问 Android/data 及其子目录。\n因此，你必须通过 SAF（Storage Access Framework） 授予 SFS汉化安装器 访问 内部储存/${Constants.SFS_DATA_DIRECTORY} 目录的权限后才可以安装汉化。\n\n请在接下来的页面中，勿进行其他操作，直接点击底部的“使用此文件夹”按钮以完成授权。\n如果你无法完成授权，请尝试前往设置使用高级权限授权！")
            },
            confirmButton = {
                TextButton(onClick = {
                    openDialog = false
                    onRequestPermissionsClicked()
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
        }, {
            Icon(
                Icons.Default.Update,
                contentDescription = null
            )
        }, text = {
            Text("新版本：9.9.9 (99)")
        }, iconColors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent
        ), colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
private fun LazyItemScope.InstallCard(
    btnInstallOnClick: () -> Unit,
    enableInstallButton: Boolean
) {
    var openChooseDialog by remember { mutableStateOf(false) }
    if (openChooseDialog) {
        AlertDialog(
            onDismissRequest = { openChooseDialog = false },
            title = { Text("选择要安装的汉化") },
            text = {
                Text("")
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
            Text("当前选择：")
            Text(
                "SFS简体中文语言包 (简体中文)"
            )
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
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("选择汉化")
                    }
                }
                TextButton(onClick = {}) {
                    Text("保存到")
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = {
                        btnInstallOnClick()
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
        colors = colors, onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
    MainLayout(uiState = MainState.Granted)
}
