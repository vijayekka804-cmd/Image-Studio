package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.ads.MobileAds
import com.example.data.AppDatabase
import com.example.data.HistoryRepository
import com.example.ui.screens.BatchScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ImageCompressorViewModel
import com.example.viewmodel.ImageCompressorViewModelFactory

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.ui.screens.PdfToolsScreen
import com.example.utils.NetworkMonitor
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

class MainActivity : ComponentActivity() {
    // Camera photo Uri storage so the activity can grant temporary permissions
    private var cameraPhotoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Google Mobile Ads SDK
        try {
            MobileAds.initialize(this) {}
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initialize Room Local Database & Repository
        val database = AppDatabase.getDatabase(this)
        val repository = HistoryRepository(database.historyDao())

        // Create ViewModel
        val factory = ImageCompressorViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, factory)[ImageCompressorViewModel::class.java]

        // Network Monitor
        val networkMonitor = NetworkMonitor(this)

        setContent {
            // Keep Dark mode state reactively in MainActivity
            val systemDarkTheme = isSystemInDarkTheme()
            var isDarkTheme by remember { mutableStateOf(systemDarkTheme) }

            // Continuous network status monitoring
            val isConnectedState by networkMonitor.isConnectedFlow.collectAsState(initial = true)
            var isCheckingConnection by remember { mutableStateOf(false) }
            var manualConnectedState by remember { mutableStateOf<Boolean?>(null) }
            val coroutineScope = rememberCoroutineScope()

            val effectiveConnected = manualConnectedState ?: isConnectedState

            // Track if the app has ever been connected to show appropriate text
            var hasBeenConnected by remember { mutableStateOf(false) }
            LaunchedEffect(effectiveConnected) {
                if (effectiveConnected) {
                    hasBeenConnected = true
                }
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()

                Box(modifier = Modifier.fillMaxSize()) {
                    if (!effectiveConnected) {
                        // Prominent, user-friendly offline blocking overlay
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .background(
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                        RoundedCornerShape(48.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = "No Internet",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Text(
                                text = if (hasBeenConnected) "Internet Connection Lost" else "Internet Connection Required",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = if (hasBeenConnected) 
                                    "An active internet connection is required. Reconnecting..." 
                                    else "Please connect to Wi-Fi or mobile data to use Image Studio.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            if (isCheckingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Verifying connection...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Button(
                                    onClick = {
                                        isCheckingConnection = true
                                        coroutineScope.launch {
                                            val actual = networkMonitor.checkActualInternet()
                                            manualConnectedState = actual
                                            isCheckingConnection = false
                                        }
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)
                                ) {
                                    Text("Retry Connection Check", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // Standard App Scaffold and Navigation
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = "home",
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                composable("home") {
                                    HomeScreen(
                                        viewModel = viewModel,
                                        onNavigateToWizard = { toolType -> navController.navigate("wizard/$toolType") },
                                        darkTheme = isDarkTheme,
                                        onToggleTheme = { isDarkTheme = !isDarkTheme }
                                    )
                                }

                                composable("wizard/{toolType}") { backStackEntry ->
                                    val toolType = backStackEntry.arguments?.getString("toolType") ?: "IMAGE_TO_PDF"
                                    com.example.ui.screens.WizardScreen(
                                        viewModel = viewModel,
                                        toolType = toolType,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }

                                composable("editor") {
                                    EditorScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }

                                composable("batch") {
                                    BatchScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }

                                composable("pdf_tools/{mode}") { backStackEntry ->
                                    val mode = backStackEntry.arguments?.getString("mode") ?: "COMPRESS"
                                    PdfToolsScreen(
                                        viewModel = viewModel,
                                        toolMode = mode,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
