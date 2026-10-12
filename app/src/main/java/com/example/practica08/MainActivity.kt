package com.example.practica08
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ==========================================
// 3. CAPA DE DATOS: ENTIDAD (@Entity)
// ==========================================
@Entity(tableName = "tabla_equipos")
data class Equipo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombreEquipo: String,
    val responsablePrestamo: String
)

// ==========================================
// 4. DATA ACCESS OBJECT (@Dao)
// ==========================================
@Dao
interface EquipoDao {
    // Flow permite que la UI reaccione automáticamente a cualquier cambio en la BD
    @Query("SELECT * FROM tabla_equipos ORDER BY id DESC")
    fun obtenerTodosLosEquipos(): Flow<List<Equipo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertarEquipo(equipo: Equipo)

    @Delete
    fun eliminarEquipo(equipo: Equipo)
}

// ==========================================
// 5. BASE DE DATOS LOCAL (@Database y Singleton)
// ==========================================
@Database(entities = [Equipo::class], version = 1, exportSchema = false)
abstract class InventarioDatabase : RoomDatabase() {
    abstract fun equipoDao(): EquipoDao

    companion object {
        @Volatile
        private var INSTANCE: InventarioDatabase? = null

        fun getDatabase(context: Context): InventarioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InventarioDatabase::class.java,
                    "inventario_db"
                )
                    .allowMainThreadQueries()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ==========================================
// 6. VIEWMODEL (Conecta la BD con Jetpack Compose)
// ==========================================
class InventarioViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = InventarioDatabase.getDatabase(application).equipoDao()

    // Convierte el Flow de la BD en un State reactivo para Compose
    val listaEquipos = dao.obtenerTodosLosEquipos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun registrarEquipo(nombre: String, responsable: String) {

            dao.insertarEquipo(Equipo(nombreEquipo = nombre, responsablePrestamo = responsable))

    }

    fun borrarEquipo(equipo: Equipo) {

            dao.eliminarEquipo(equipo)

    }
}

// ==========================================
// 7. ACTIVITY E INTERFAZ DE USUARIO (Compose)
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppInventarioScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInventarioScreen(viewModel: InventarioViewModel = viewModel()) {
    // Observamos los datos en tiempo real
    val equipos by viewModel.listaEquipos.collectAsState()

    var nombreInput by remember { mutableStateOf("") }
    var responsableInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Préstamo de Equipo") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Formulario de Ingreso
            OutlinedTextField(
                value = nombreInput,
                onValueChange = { nombreInput = it },
                label = { Text("Nombre del equipo (Ej. Proyector, Laptop)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = responsableInput,
                onValueChange = { responsableInput = it },
                label = { Text("Responsable / Solicitante") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (nombreInput.isNotBlank() && responsableInput.isNotBlank()) {
                        viewModel.registrarEquipo(nombreInput, responsableInput)
                        nombreInput = ""
                        responsableInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Registrar Préstamo")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Equipos registrados:", style = MaterialTheme.typography.titleMedium)

            // Lista reactiva de la Base de Datos
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(equipos) { equipo ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = equipo.nombreEquipo, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(text = "Prestado a: ${equipo.responsablePrestamo}", fontSize = 14.sp)
                            }
                            IconButton(onClick = { viewModel.borrarEquipo(equipo) }) {
                                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}