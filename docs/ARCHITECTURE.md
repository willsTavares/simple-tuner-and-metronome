# Pitch & Metronome — Arquitetura do Projeto

## Visão Geral

Aplicativo Android nativo em Kotlin com dois módulos funcionais principais:
- **Metrônomo** — geração de pulso rytmico com baixíssima latência
- **Afinador** — captura de áudio em tempo real e detecção de pitch

---

## Estrutura de Pastas

```
app/src/main/
├── cpp/                                  ← Código C++ (NDK / Oboe)
│   ├── CMakeLists.txt
│   ├── metronome/
│   │   └── MetronomeEngine.cpp           ← AAudio output stream via Oboe
│   └── tuner/
│       ├── PitchDetectorEngine.cpp       ← AAudio input stream via Oboe
│       └── YinAlgorithm.cpp             ← Algoritmo YIN de detecção de pitch
│
└── java/com/pitchandmetronome/
    ├── PitchAndMetronomeApp.kt           ← Application (Hilt)
    ├── MainActivity.kt                   ← Single Activity
    │
    ├── core/
    │   ├── di/                           ← Módulos Hilt
    │   │   ├── AppModule.kt
    │   │   ├── AudioModule.kt
    │   │   └── RepositoryModule.kt
    │   ├── audio/
    │   │   ├── AudioEngineConfig.kt      ← Constantes de áudio (sample rate, buffer)
    │   │   └── AudioPermissionManager.kt ← Gerenciamento de RECORD_AUDIO
    │   └── utils/
    │       ├── FrequencyUtils.kt         ← Conversão Hz ↔ nota musical
    │       └── CoroutineDispatchers.kt   ← Dispatchers customizados
    │
    ├── domain/                           ← Pure Kotlin — zero dependências Android
    │   ├── model/
    │   │   ├── metronome/
    │   │   │   ├── BeatConfig.kt
    │   │   │   ├── BeatEvent.kt
    │   │   │   ├── TimeSignature.kt
    │   │   │   ├── SoundProfile.kt
    │   │   │   └── MetronomeState.kt
    │   │   └── tuner/
    │   │       ├── Note.kt
    │   │       ├── PitchResult.kt
    │   │       ├── TunerConfig.kt
    │   │       └── TunerState.kt
    │   ├── repository/
    │   │   ├── IMetronomeRepository.kt
    │   │   └── ITunerRepository.kt
    │   └── usecase/
    │       ├── metronome/
    │       │   ├── StartMetronomeUseCase.kt
    │       │   ├── StopMetronomeUseCase.kt
    │       │   ├── SetBpmUseCase.kt
    │       │   ├── SetTimeSignatureUseCase.kt
    │       │   └── ObserveMetronomeStateUseCase.kt
    │       └── tuner/
    │           ├── StartTunerUseCase.kt
    │           ├── StopTunerUseCase.kt
    │           └── ObservePitchUseCase.kt
    │
    ├── data/                             ← Implementações dos repositórios
    │   ├── repository/
    │   │   ├── MetronomeRepositoryImpl.kt
    │   │   └── TunerRepositoryImpl.kt
    │   └── preferences/
    │       ├── UserPreferencesDataStore.kt
    │       └── PreferencesKeys.kt
    │
    ├── audio/                            ← Bridges Kotlin ↔ NDK
    │   ├── metronome/
    │   │   ├── IMetronomeEngine.kt       ← Interface de contrato
    │   │   ├── MetronomeEngineImpl.kt    ← JNI bridge → MetronomeEngine.cpp
    │   │   └── BeatScheduler.kt         ← Agenda beats no thread de áudio
    │   └── tuner/
    │       ├── IPitchDetector.kt         ← Interface de contrato
    │       ├── PitchDetectorImpl.kt      ← JNI bridge → PitchDetectorEngine.cpp
    │       └── AudioCaptureConfig.kt     ← Parâmetros de captura
    │
    ├── metronome/                        ← Feature: Metrônomo
    │   ├── MetronomeViewModel.kt
    │   ├── MetronomeUiState.kt
    │   └── ui/
    │       ├── MetronomeScreen.kt
    │       ├── BeatVisualizerComponent.kt
    │       ├── BpmControlComponent.kt
    │       └── TimeSignaturePickerComponent.kt
    │
    └── tuner/                            ← Feature: Afinador
        ├── TunerViewModel.kt
        ├── TunerUiState.kt
        └── ui/
            ├── TunerScreen.kt
            ├── TunerNeedleComponent.kt
            ├── NoteDisplayComponent.kt
            └── FrequencyDisplayComponent.kt
```

---

## Camadas e Responsabilidades

### 1. Audio Engine (`cpp/` + `audio/`)

**É a camada mais crítica** — roda em threads de tempo-real, nunca bloqueia.

| Componente | Responsabilidade |
|---|---|
| `MetronomeEngine.cpp` | Abre um **AAudio output stream** via Oboe. O callback de áudio gera amostras PCM de click/beep com precisão de sample (± 20µs). Nunca aloca memória no callback. |
| `PitchDetectorEngine.cpp` | Abre um **AAudio input stream** via Oboe. Acumula amostras em um ring-buffer e passa para `YinAlgorithm` a cada frame. |
| `YinAlgorithm.cpp` | Implementação do algoritmo YIN para detecção de pitch monofônico. Retorna frequência em Hz + índice de confiança. |
| `IMetronomeEngine.kt` | Contrato Kotlin que o ViewModel enxerga. Expõe `beatFlow: Flow<BeatEvent>`. |
| `IPitchDetector.kt` | Contrato Kotlin. Expõe `pitchFlow: Flow<PitchResult?>`. |
| `MetronomeEngineImpl.kt` / `PitchDetectorImpl.kt` | JNI bridges: traduzem chamadas Kotlin para chamadas nativas e callbacks nativos para emissões no Flow via `callbackFlow`. |

**Por que NDK / Oboe?**
> A API Java `AudioTrack` tem latência de piso ~50–100ms. AAudio via Oboe atinge < 10ms em dispositivos com driver de baixa latência. Isso é fundamental para um metrônomo profissional.

---

### 2. Domain (`domain/`)

Kotlin puro — **zero imports Android**. Contém as regras de negócio independentes de framework.

| Componente | Responsabilidade |
|---|---|
| `model/` | Data classes e sealed classes que representam o estado do negócio. |
| `repository/` | **Interfaces** que definem contratos de acesso a dados. A camada domain não sabe como os dados são persistidos. |
| `usecase/` | Cada use case é uma classe com um único método `invoke`. Orquestra engine + repository. São testáveis com MockK sem Android. |

---

### 3. Data (`data/`)

Implementa os contratos definidos em `domain/repository/`.

| Componente | Responsabilidade |
|---|---|
| `MetronomeRepositoryImpl` | Mantém o `MetronomeState` em um `MutableStateFlow`. Persiste BPM e compasso via DataStore. |
| `TunerRepositoryImpl` | Persiste `TunerConfig` (referência A4, sample rate) via DataStore. |
| `UserPreferencesDataStore` | Wrapper sobre `androidx.datastore`. Serializa preferências do usuário. |

---

### 4. ViewModel (`metronome/` + `tuner/`)

Ponte entre Domain e UI. **Nunca referencia View diretamente.**

| Responsabilidade |
|---|
| Chama use cases dentro de `viewModelScope` |
| Transforma `MetronomeState` / `TunerState` (domínio) em `MetronomeUiState` / `TunerUiState` (UI) |
| Expõe `StateFlow<UiState>` — a Compose UI apenas lê |
| Trata erros e estados de loading |
| Cancela coroutines automaticamente quando o ViewModel é destruído |

---

### 5. UI (`metronome/ui/` + `tuner/ui/`)

Compose declarativo — **lê estado, emite eventos, sem lógica de negócio.**

| Componente | Responsabilidade |
|---|---|
| `MetronomeScreen` | Composable raiz da feature. Coleta o `StateFlow` do ViewModel via `collectAsStateWithLifecycle`. |
| `BeatVisualizerComponent` | Animação visual sincronizada com `BeatEvent`. Usa `LaunchedEffect` + `animateFloatAsState`. |
| `BpmControlComponent` | Slider + botões +/-. Emite `onBpmChange(Int)` ao ViewModel. |
| `TunerNeedleComponent` | Ponteiro animado de -50 a +50 cents. Canvas API do Compose. |

---

## Fluxo de Dados

### Metrônomo (Start → Beat Visual)

```
UI: onPlayPause()
    └─▶ MetronomeViewModel.onPlayPause()
            └─▶ StartMetronomeUseCase(config)
                    ├─▶ MetronomeRepositoryImpl.updateState(isPlaying = true)
                    └─▶ IMetronomeEngine.start(config)
                                └─▶ [NDK] AAudio callback thread
                                        └─▶ BeatEvent emitido via JNI → callbackFlow
                                                └─▶ ObserveMetronomeStateUseCase
                                                        └─▶ MetronomeViewModel (StateFlow)
                                                                └─▶ UI: BeatVisualizerComponent
```

### Afinador (Microfone → Nota na Tela)

```
UI: onStartTuner()
    └─▶ TunerViewModel.onStartTuner()
            └─▶ StartTunerUseCase()
                    └─▶ IPitchDetector.startDetection(config)
                                └─▶ [NDK] AAudio input callback thread
                                        └─▶ YIN algorithm → frequência
                                                └─▶ PitchResult emitido via callbackFlow
                                                        └─▶ ObservePitchUseCase (mapeamento Hz → Nota)
                                                                └─▶ TunerViewModel (StateFlow)
                                                                        └─▶ UI: TunerNeedleComponent
                                                                                   NoteDisplayComponent
```

---

## Decisões Técnicas

| Decisão | Alternativa Descartada | Justificativa |
|---|---|---|
| **Oboe / AAudio (NDK)** | `AudioTrack` Java | Latência < 10ms vs 50-100ms. Crítico para metrônomo profissional. |
| **Algoritmo YIN** | FFT simples | YIN tem melhor precisão para áudio monofônico (instrumentos). FFT fica como análise secundária. |
| **MVVM + Clean Architecture** | MVI, MVP | MVVM é idiomático com Jetpack; separação de camadas permite testar domain sem Android. |
| **Hilt** | Koin, manual DI | Integração nativa com ViewModel, WorkManager e Navigation. Verificação em compile-time. |
| **Kotlin Flow (callbackFlow)** | LiveData, RxJava | Integração nativa com coroutines; `callbackFlow` é o padrão para pontear callbacks nativos. |
| **DataStore** | SharedPreferences | Type-safe, não bloqueia a main thread, suporte a Flow nativo. |
| **Single Activity** | Multi-Activity | Compose gerencia backstack via `NavHost`; Activity única simplifica o ciclo de vida. |
| **minSdk 26 (Android 8.0)** | minSdk 21 | AAudio está disponível desde API 26. Necessário para o engine de baixa latência. |

---

## Contratos de Threading

| Thread | O que roda |
|---|---|
| Main / UI thread | Compose rendering, eventos de toque |
| `viewModelScope` (Default) | Use cases, transformações de Flow |
| AAudio callback thread (RT) | Geração de amostras PCM, YIN — **nunca aloca, nunca bloqueia** |
| `Dispatchers.IO` | DataStore read/write |

---

## Permissões Necessárias

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Indica suporte preferencial a latência baixa -->
<uses-feature android:name="android.hardware.audio.low_latency" android:required="false" />
<uses-feature android:name="android.hardware.audio.pro" android:required="false" />
```
