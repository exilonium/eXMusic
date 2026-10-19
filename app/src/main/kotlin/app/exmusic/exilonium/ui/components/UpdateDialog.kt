package app.exmusic.exilonium.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.exmusic.core.ui.LocalAppearance
import app.exmusic.exilonium.R
import app.exmusic.exilonium.UpdateState
import app.exmusic.exilonium.setUpdateState
import app.exmusic.exilonium.ui.components.themed.DefaultDialog
import app.exmusic.exilonium.ui.components.themed.DialogTextButton
import app.exmusic.exilonium.ui.components.themed.LinearProgressIndicator
import app.exmusic.exilonium.updateState
import app.exmusic.exilonium.utils.bold
import app.exmusic.exilonium.utils.center
import app.exmusic.exilonium.utils.downloadApk
import app.exmusic.exilonium.utils.launchInstall
import app.exmusic.exilonium.utils.medium
import app.exmusic.exilonium.utils.secondary
import app.exmusic.exilonium.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    modifier: Modifier = Modifier
) {
    val state by updateState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (_, typography) = LocalAppearance.current

    AnimatedContent(
        targetState = state,
        modifier = modifier,
        contentKey = { it::class },
        label = "update_dialog"
    ) { currentState ->
        when (currentState) {
            is UpdateState.Available -> DefaultDialog(
                onDismiss = { setUpdateState(UpdateState.Idle) }
            ) {
                BasicText(
                    text = stringResource(R.string.new_version_available),
                    style = typography.s.semiBold.center
                )

                Spacer(Modifier.height(8.dp))

                BasicText(
                    text = currentState.release.name ?: currentState.release.tag,
                    style = typography.m.bold.center
                )

                currentState.release.markdown?.let { body ->
                    val changelog = body.lines()
                        .map { it.trim() }
                        .filter {
                            it.isNotBlank() &&
                                !it.startsWith("#") &&
                                !it.startsWith("Which", ignoreCase = true) &&
                                !it.startsWith("**Which", ignoreCase = true)
                        }
                        .take(6)

                    if (changelog.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            changelog.forEach { rawLine ->
                                val isBullet = listOf("*", "-", "•").any { rawLine.startsWith(it) }
                                val text = (if (isBullet) {
                                    rawLine.replaceFirst(Regex("""^[\*\-•]\s*"""), "")
                                } else {
                                    rawLine
                                }).replace("**", "").replace("`", "")

                                if (isBullet) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        BasicText(
                                            text = "•",
                                            style = typography.xxs.bold.secondary
                                        )
                                        BasicText(
                                            text = text,
                                            style = typography.xxs.medium.secondary,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    BasicText(
                                        text = text,
                                        style = typography.xxs.medium.secondary,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DialogTextButton(
                        text = stringResource(R.string.update_skip),
                        onClick = { setUpdateState(UpdateState.Idle) }
                    )

                    DialogTextButton(
                        text = stringResource(R.string.update_download_now),
                        primary = true,
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                downloadApk(context, currentState.apkUrl)
                            }
                        }
                    )
                }
            }

            is UpdateState.Downloading -> DefaultDialog(
                onDismiss = { }
            ) {
                BasicText(
                    text = stringResource(R.string.update_download),
                    style = typography.s.semiBold.center
                )

                Spacer(Modifier.height(16.dp))

                val progress = currentState.progress
                if (progress > 0f) {
                    val animated by animateFloatAsState(targetValue = progress, label = "dl_progress")
                    LinearProgressIndicator(
                        progress = animated,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    BasicText(
                        text = stringResource(R.string.update_download_progress, (progress * 100).toInt()),
                        style = typography.xs.medium.secondary.center
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            is UpdateState.ReadyToInstall -> DefaultDialog(
                onDismiss = { setUpdateState(UpdateState.Idle) }
            ) {
                BasicText(
                    text = stringResource(R.string.update_ready),
                    style = typography.s.semiBold.center
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DialogTextButton(
                        text = stringResource(R.string.update_skip),
                        onClick = { setUpdateState(UpdateState.Idle) }
                    )

                    DialogTextButton(
                        text = stringResource(R.string.update_install),
                        primary = true,
                        onClick = {
                            launchInstall(context, currentState.uri)
                            setUpdateState(UpdateState.Idle)
                        }
                    )
                }
            }

            is UpdateState.Idle -> Unit
        }
    }
}
