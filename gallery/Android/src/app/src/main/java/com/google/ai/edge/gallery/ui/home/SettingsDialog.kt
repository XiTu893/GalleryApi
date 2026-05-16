/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.home

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.ai.edge.gallery.BuildConfig
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.proto.Theme
import com.google.ai.edge.gallery.ui.common.ClickableLink
import com.google.ai.edge.gallery.ui.common.tos.AppTosDialog
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.ThemeSettings
import com.google.ai.edge.gallery.ui.theme.labelSmallNarrow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

private val THEME_OPTIONS = listOf(Theme.THEME_AUTO, Theme.THEME_LIGHT, Theme.THEME_DARK)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
  curThemeOverride: Theme,
  modelManagerViewModel: ModelManagerViewModel,
  onDismissed: () -> Unit,
) {
  var selectedTheme by remember { mutableStateOf(curThemeOverride) }
  var hfToken by remember { mutableStateOf(modelManagerViewModel.getTokenStatusAndData().data) }
  val dateFormatter = remember {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
      .withZone(ZoneId.systemDefault())
      .withLocale(Locale.getDefault())
  }
  var customHfToken by remember { mutableStateOf("") }
  var isFocused by remember { mutableStateOf(false) }
  val focusRequester = remember { FocusRequester() }
  val interactionSource = remember { MutableInteractionSource() }
  var showTos by remember { mutableStateOf(false) }

  Dialog(onDismissRequest = onDismissed) {
    val focusManager = LocalFocusManager.current
    Card(
      modifier =
        Modifier.fillMaxWidth().clickable(
          interactionSource = interactionSource,
          indication = null,
        ) {
          focusManager.clearFocus()
        },
      shape = RoundedCornerShape(16.dp),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Column {
          Text(
            "设置",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp),
          )
          Text(
            "应用版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = labelSmallNarrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.offset(y = (-6).dp),
          )
        }

        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          val context = LocalContext.current

          Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
            Text(
              "主题",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            )
            MultiChoiceSegmentedButtonRow {
              THEME_OPTIONS.forEachIndexed { index, theme ->
                SegmentedButton(
                  shape =
                    SegmentedButtonDefaults.itemShape(index = index, count = THEME_OPTIONS.size),
                  onCheckedChange = {
                    selectedTheme = theme
                    ThemeSettings.themeOverride.value = theme
                    modelManagerViewModel.saveThemeOverride(theme)
                    val uiModeManager =
                      context.applicationContext.getSystemService(Context.UI_MODE_SERVICE)
                        as UiModeManager
                    if (theme == Theme.THEME_AUTO) {
                      uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
                    } else if (theme == Theme.THEME_LIGHT) {
                      uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
                    } else {
                      uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
                    }
                  },
                  checked = theme == selectedTheme,
                  label = { Text(themeLabel(theme)) },
                )
              }
            }
          }

          Column(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              "HuggingFace 访问令牌",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            )
            val curHfToken = hfToken
            if (curHfToken != null && curHfToken.accessToken.isNotEmpty()) {
              Text(
                curHfToken.accessToken.substring(0, min(16, curHfToken.accessToken.length)) + "...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                "过期时间: ${dateFormatter.format(Instant.ofEpochMilli(curHfToken.expiresAtMs))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              Text(
                "不可用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                "下载受限模型时将自动获取令牌",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              OutlinedButton(
                onClick = {
                  modelManagerViewModel.clearAccessToken()
                  hfToken = null
                },
                enabled = curHfToken != null,
              ) {
                Text("清除")
              }
              val handleSaveToken = {
                modelManagerViewModel.saveAccessToken(
                  accessToken = customHfToken,
                  refreshToken = "",
                  expiresAt = System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 10,
                )
                hfToken = modelManagerViewModel.getTokenStatusAndData().data
                focusManager.clearFocus()
              }
              BasicTextField(
                value = customHfToken,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { handleSaveToken() }),
                modifier =
                  Modifier.fillMaxWidth()
                    .padding(top = 4.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused },
                onValueChange = { customHfToken = it },
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
              ) { innerTextField ->
                Box(
                  modifier =
                    Modifier.border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color =
                          if (isFocused) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                      )
                      .height(40.dp),
                  contentAlignment = Alignment.CenterStart,
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                      if (customHfToken.isEmpty()) {
                        Text(
                          "手动输入令牌",
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          style = MaterialTheme.typography.bodySmall,
                        )
                      }
                      innerTextField()
                    }
                    if (customHfToken.isNotEmpty()) {
                      IconButton(modifier = Modifier.offset(x = 1.dp), onClick = handleSaveToken) {
                        Icon(
                          Icons.Rounded.CheckCircle,
                          contentDescription = stringResource(R.string.cd_done_icon),
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          ApiServiceControlPanel()

          Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
            Text(
              "第三方库",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            )
            OutlinedButton(
              onClick = {
                val intent = Intent(context, OssLicensesMenuActivity::class.java)
                context.startActivity(intent)
              }
            ) {
              Text("查看许可证")
            }
          }

          Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
            Text(
              stringResource(R.string.settings_dialog_tos_title),
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            )
            OutlinedButton(onClick = { showTos = true }) {
              Text(stringResource(R.string.settings_dialog_view_app_terms_of_service))
            }
            ClickableLink(
              url = "https://ai.google.dev/gemma/terms",
              linkText = stringResource(R.string.tos_dialog_title_gemma),
              modifier = Modifier.padding(top = 4.dp),
            )
            ClickableLink(
              url = "https://ai.google.dev/gemma/prohibited_use_policy",
              linkText = stringResource(R.string.settings_dialog_gemma_prohibited_use_policy),
              modifier = Modifier.padding(top = 8.dp),
            )
          }

          DonationSection(context)
        }

        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          horizontalArrangement = Arrangement.End,
        ) {
          Button(onClick = { onDismissed() }) { Text("关闭") }
        }
      }
    }
  }

  if (showTos) {
    AppTosDialog(onTosAccepted = { showTos = false }, viewingMode = true)
  }
}

@Composable
private fun DonationSection(context: Context) {
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "支持项目",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "如果这个项目对你有帮助，欢迎扫描下方二维码捐赠支持！",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        val qrBitmap = remember {
            try {
                context.assets.open("QrReward.jpg").use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (_: Exception) {
                try {
                    val resId = context.resources.getIdentifier("qr_reward", "drawable", context.packageName)
                    if (resId != 0) BitmapFactory.decodeResource(context.resources, resId) else null
                } catch (_: Exception) {
                    null
                }
            }
        }
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "捐赠二维码",
                modifier = Modifier
                    .size(180.dp)
                    .clickable {
                        try {
                            val wechatIntent = Intent(Intent.ACTION_VIEW)
                            wechatIntent.setPackage("com.tencent.mm")
                            wechatIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(wechatIntent)
                        } catch (_: Exception) {
                            val storeIntent = Intent(Intent.ACTION_VIEW)
                            storeIntent.data = android.net.Uri.parse("market://details?id=com.tencent.mm")
                            storeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            try {
                                context.startActivity(storeIntent)
                            } catch (_: Exception) {
                                val webIntent = Intent(Intent.ACTION_VIEW)
                                webIntent.data = android.net.Uri.parse("https://weixin.qq.com/")
                                webIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(webIntent)
                            }
                        }
                    },
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            "点击二维码打开微信扫码",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun themeLabel(theme: Theme): String {
  return when (theme) {
    Theme.THEME_AUTO -> "跟随系统"
    Theme.THEME_LIGHT -> "浅色"
    Theme.THEME_DARK -> "深色"
    else -> "未知"
  }
}
