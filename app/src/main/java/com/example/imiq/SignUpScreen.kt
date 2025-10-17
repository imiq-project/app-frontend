package com.example.imiq

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SignUpScreen(
    onSignUpComplete: () -> Unit = {},
    viewModel: LoginViewModel = viewModel()
) {
    var code by remember { mutableStateOf("") }
    val loginState by viewModel.loginState.collectAsState()
    val uriHandler = LocalUriHandler.current

    // Our purple colors
    val darkPurple = Color(0xFF7C4DFF)
    val mediumPurple = Color(0xFF9575CD)
    val lightPurple = Color(0xFFE1BEE7)

    // Main container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(darkPurple, mediumPurple, lightPurple)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(100.dp))

            // Logo
            Text(
                text = "IMIQ",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Sign up",
                fontSize = 24.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Code field (changed from email/password)
            TextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Enter Code") },
                placeholder = { Text("123-456-789") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.3f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.3f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                enabled = loginState !is LoginState.Loading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error message
            if (loginState is LoginState.Error) {
                Text(
                    text = (loginState as LoginState.Error).message,
                    color = Color.Red.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Get started button
            Button(
                onClick = {
                    viewModel.login(code)
                },
                enabled = code.isNotBlank() && loginState !is LoginState.Loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.3f),
                    disabledContainerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (loginState is LoginState.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row {
                        Text("get started", color = Color.White, fontSize = 16.sp)
                        Text(" →", color = Color.White, fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Registration link
            val annotatedString = buildAnnotatedString {
                withStyle(style = SpanStyle(color = Color.White)) {
                    append("Don't have a code? ")
                }
                pushStringAnnotation(tag = "URL", annotation = "https://imiq.ovgu.de/anmeldung")
                withStyle(
                    style = SpanStyle(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append("Register here")
                }
                pop()
            }

            ClickableText(
                text = annotatedString,
                onClick = { offset ->
                    annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item)
                        }
                }
            )
        }
    }

    // Handle successful login
    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            onSignUpComplete() // Navigate to profile setup screen
            viewModel.resetState() // Reset for next time
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SignUpScreenPreview() {
    SignUpScreen()
}