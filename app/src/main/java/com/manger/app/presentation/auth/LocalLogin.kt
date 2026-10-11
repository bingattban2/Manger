package com.manger.app.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.manger.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.manger.app.application.auth.SignInUseCase
import com.manger.app.application.auth.SignInResult
import kotlinx.coroutines.launch
import com.manger.app.domain.LocalSession
import com.manger.app.domain.SupervisorDutyDecision

@Composable internal fun LocalLogin(
    signIn: SignInUseCase,
    onLogin: (LocalSession) -> Unit,
    expectedUsername: String? = null
) {
    val scope = rememberCoroutineScope()
    var signingIn by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf<LocalSession?>(null) }
    var dutyResult by remember { mutableStateOf<SupervisorDutyDecision?>(null) }
    BackHandler(enabled = blocked != null) { blocked = null; dutyResult = null; error = null }
    MaterialTheme {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                Text(if (expectedUsername == null) "تسجيل الدخول" else "أدخل كلمة المرور لمتابعة العمل", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(22.dp))
                if (blocked != null) ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("التحقق من تحضير المشرف", fontWeight = FontWeight.Bold)
                        Text("${blocked!!.displayName} • ${blocked!!.site}")
                        Text(dutyResult?.message.orEmpty(), color = MaterialTheme.colorScheme.error)
                        if (dutyResult?.reason == com.manger.app.domain.DutyReason.NO_TODAY_MARK) {
                            Button(onClick = {
                                val account = blocked ?: return@Button
                                scope.launch {
                                    runCatching { signIn.requestDutyConfirmation(account) }
                                        .onSuccess { error = "تم تسجيل طلبك محلياً للإدارة. لن يُفتح حسابك حتى يتم اعتماد حضورك." }
                                        .onFailure { error = it.localizedMessage ?: "تعذر تسجيل الطلب" }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("إرسال طلب إثبات حضور للإدارة") }
                        }
                        if (error != null) Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            val account = blocked ?: return@OutlinedButton
                            scope.launch {
                                runCatching { signIn.recheck(account) }.onSuccess { decision ->
                                    if (decision.allowed) { blocked = null; dutyResult = null; password = ""; onLogin(account) }
                                    else dutyResult = decision
                                }.onFailure { error = it.localizedMessage }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("إعادة التحقق من الحضور") }
                        TextButton(onClick = { blocked = null; dutyResult = null; error = null },
                            modifier = Modifier.fillMaxWidth()) { Text("العودة لتسجيل الدخول") }
                    }
                } else ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(username, { username = it; error = null },
                            label = { Text("اسم المستخدم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(password, { password = it; error = null },
                            label = { Text("كلمة المرور") }, singleLine = true,
                            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "إخفاء" else "إظهار") } },
                            modifier = Modifier.fillMaxWidth())
                        if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Button(onClick = {
                            if (!signingIn) {
                                signingIn = true
                                val submittedName = username
                                val submittedPassword = password
                                scope.launch {
                                    try {
                                        when (val result = signIn.signIn(submittedName, submittedPassword)) {
                                            SignInResult.InvalidCredentials -> error = "اسم المستخدم أو كلمة المرور غير صحيحة"
                                            is SignInResult.Granted -> {
                                                password = ""
                                                if (expectedUsername != null && result.account.username != expectedUsername) {
                                                    error = "لإكمال الجلسة أدخل بيانات الحساب نفسه: $expectedUsername"
                                                } else onLogin(result.account)
                                            }
                                            is SignInResult.DutyBlocked -> {
                                                password = ""; blocked = result.account
                                                dutyResult = result.decision; error = null
                                            }
                                        }
                                    } catch (e: Exception) {
                                        error = "تعذر التحقق من الحساب: ${e.localizedMessage}"
                                    } finally { signingIn = false }
                                }
                            }
                        }, enabled = !signingIn, modifier = Modifier.fillMaxWidth()) {
                            Text(if (signingIn) "جارٍ التحقق..." else "دخول")
                        }
                    }
                }
                if (expectedUsername == null) {
                    Spacer(Modifier.height(12.dp))
                    Text("الحسابات الجديدة تحتاج إلى تهيئة مصادقة السيرفر أولاً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
