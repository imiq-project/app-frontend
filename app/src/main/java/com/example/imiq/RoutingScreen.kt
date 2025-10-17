package com.example.imiq

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.CheckCircle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingScreen(onBackClick: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val locationHelper = remember { LocationHelper(context) }
    val scope = rememberCoroutineScope()

    // State variables
    var fromLocation by remember { mutableStateOf("") }
    var toLocation by remember { mutableStateOf("") }
    var routePoints by remember { mutableStateOf<List<List<Double>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var estimatedTime by remember { mutableStateOf("--") }
    var currentLatLng by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Get current location when screen loads
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                if (locationHelper.hasLocationPermission(context)) {
                    val location = locationHelper.getCurrentLocation()
                    location?.let {
                        currentLatLng = it
                        fromLocation = "Current Location"
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Failed to get location"
            }
        }
    }

    // Function to get route from API
    fun fetchRoute() {
        scope.launch {
            try {
                isLoading = true
                errorMessage = null

                val startCoords = currentLatLng ?: Pair(52.45342, 11.45235)
                val destCoords = Pair(52.55342, 11.55235)

                val request = RouteRequest(
                    start = listOf(startCoords.first, startCoords.second),
                    destination = listOf(destCoords.first, destCoords.second),
                    profile = emptyMap()
                )

                val response = RoutingApiService.api.getRoute(request)
                routePoints = response.points

                val distance = calculateDistance(response.points)
                estimatedTime = "${(distance / 50).toInt()} mins"

            } catch (e: Exception) {
                errorMessage = "Route not found"
            } finally {
                isLoading = false
            }
        }
    }

    // Colors
    val darkPurple = Color(0xFF7C4DFF)
    val mediumPurple = Color(0xFF9575CD)
    val lightPurple = Color(0xFFE1BEE7)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Route Planning",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = darkPurple
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, "Home") },
                    label = { Text("Home") },
                    selected = true,
                    onClick = { }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, "Search") },
                    label = { Text("Search") },
                    selected = false,
                    onClick = { }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, "Profile") },
                    label = { Text("Profile") },
                    selected = false,
                    onClick = { }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top section with input fields - compact design
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(darkPurple, mediumPurple)
                        )
                    )
                    .padding(16.dp)
            ) {
                // From field
                CompactLocationField(
                    label = "From",
                    value = fromLocation,
                    onValueChange = { fromLocation = it },
                    icon = Icons.Default.LocationOn
                )

                Spacer(modifier = Modifier.height(8.dp))

                // To field
                CompactLocationField(
                    label = "To",
                    value = toLocation,
                    onValueChange = { toLocation = it },
                    icon = Icons.Default.Place
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Find Route Button
                Button(
                    onClick = { fetchRoute() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White
                    ),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = darkPurple,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = darkPurple,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Find Route",
                                color = darkPurple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                // Error message
                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            // Large Map Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Takes remaining space
                    .background(Color.White)
            ) {
                if (routePoints.isEmpty()) {
                    // Empty state
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = lightPurple,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Enter destination to find route",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    // Route loaded state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(lightPurple.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = darkPurple,
                                modifier = Modifier.size(60.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Route Found!",
                                color = darkPurple,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${routePoints.size} waypoints",
                                color = mediumPurple,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Est. time: $estimatedTime",
                                color = mediumPurple,
                                fontSize = 14.sp
                            )
                        }

                        // Route visualization placeholder (grid pattern)
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp)
                        ) {
                            // Draw simple route line visualization
                            if (routePoints.size > 1) {
                                val path = Path()
                                val firstPoint = routePoints.first()
                                path.moveTo(
                                    size.width * 0.2f,
                                    size.height * 0.3f
                                )
                                path.lineTo(
                                    size.width * 0.5f,
                                    size.height * 0.5f
                                )
                                path.lineTo(
                                    size.width * 0.8f,
                                    size.height * 0.7f
                                )

                                drawPath(
                                    path = path,
                                    color = darkPurple,
                                    style = Stroke(width = 8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Compact location field component
@Composable
fun CompactLocationField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    label,
                    color = Color.Gray.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RoutingScreenPreview() {
    RoutingScreen()
}

// Helper function to calculate distance
private fun calculateDistance(points: List<List<Double>>): Double {
    if (points.size < 2) return 0.0

    var totalDistance = 0.0
    for (i in 0 until points.size - 1) {
        val lat1 = points[i][0]
        val lon1 = points[i][1]
        val lat2 = points[i + 1][0]
        val lon2 = points[i + 1][1]

        // Simple distance calculation
        val dist = Math.sqrt(
            Math.pow(lat2 - lat1, 2.0) + Math.pow(lon2 - lon1, 2.0)
        ) * 111.0 // Convert to km
        totalDistance += dist
    }

    return totalDistance
}