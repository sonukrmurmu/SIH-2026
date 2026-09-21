Indic Translation Android App (Santali & Regional Focus)

An Android application designed to perform offline/edge translation using a custom CTranslate2 IndicTrans2 model, optimized with a local fallback dictionary and native C++ integration.
```text
/translator/
├── app/src/main/
│   ├── cpp/                       # C++ native engine & CMake configuration
│   │   ├── CMakeLists.txt         # Links native AI libraries (CTranslate2, SentencePiece) and UI components
│   │   └── native-lib.cpp         # Handles AI model boot, memory management, INT8 optimization, and dual-engine routing
│   ├── java/ or kotlin/           # App source code (UI & logic handlers)
│   │   ├── MainActivity.kt        # Handles UI input, thread management, AI routing (Relay/Bypass), and UI updates
│   │   └── DictionaryDb.kt        # Post-processes/analyzes dictionary overrides for zero-latency direct translations
│   ├── res/                       # App resources
│   │   ├── font/                  # Custom font files (e.g., noto_sans_ol_chiki for Santali)
│   │   ├── layout/
│   │   │   └── activity_main.xml  # Main UI layout (Assigned to Anamika)
│   │   └── values/
│   │       └── strings.xml        # Centralized UI text, app identity, and Spinner dropdown language arrays
│   ├── assets/                    # Local storage for offline assets
│   │   ├── dictionary.db          # SQLite dictionary database (1,000+ words for edge corrections)
│   │   ├── indictrans2_200m/      # CTranslate2 Engine 1: English -> Indic AI Model
│   │   └── indic-eng/             # CTranslate2 Engine 2: Indic -> English AI Model
│   └── AndroidManifest.xml        # App permissions and configurations
└── build.gradle.kts               # Global/App-level build configurations  ```text
🧠 Core Architecture & Components

    Native AI Layer (C++ / JNI): Located in app/src/main/cpp/, this layer handles the heavy lifting of passing text data cleanly from the Android UI into the CTranslate2 model runtime. It is a fully offline, high-performance inference engine optimized specifically for mobile ARM processors. It utilizes INT8 Quantization and strict 4-thread pooling per replica to prevent OEM background throttling (e.g., ColorOS deadlocks) and guarantees sub-3-second generation latency while actively preventing Out-of-Memory (OOM) leaks.

    Kotlin Logic (MainActivity.kt): Manages user input from the UI, triggers the C++ translation engine, receives the output, and updates the display screen. It dynamically routes text through one of three pathways securely on a background thread boosted with Thread.MAX_PRIORITY:

        Single Engine: Direct translation (English -> Indic or Indic -> English).

        Dual-Engine Relay: For Indic-to-Indic translations (e.g., Hindi -> Santali), it executes a seamless background relay (Engine 2 -> English -> Engine 1) while maintaining a stable UI state.

        Safety Net: All C++ calls are wrapped in robust memory-state checks to prevent silent native crashes (SIGSEGV) from freezing the UI.

    Dictionary Correction Kit (DictionaryDb): An intelligent post-processing layer and local SQLite interceptor. Before passing complex regional terminology to the AI matrix, it cross-references a local database (1,000+ words) to correct occasional model inaccuracies for specific terminology. If an exact match is found, it bypasses the heavy C++ math entirely, achieving 0ms latency and 100% precision for critical edge cases.

    UI Layout & Resources (activity_main.xml & strings.xml): Located under app/src/main/res/. Note: UI design and layout implementation are handled by Anamika. It features custom font rendering capabilities to perfectly display regional scripts like Santali Ol Chiki. Additionally, strings.xml serves as the centralized database for all static text. It defines the official app identity, UI button labels, and the string arrays that populate the source and target language dropdown Spinners, ensuring the Kotlin logic remains clean and fully localizable.

⚙️ Build & Configuration Highlights (build.gradle.kts)

Key configurations implemented in the project build files for local AI inference:

    Architecture Targeting: Configured strictly for 64-bit (arm64-v8a) libraries to prevent compatibility errors associated with massive 32-bit AI binaries.

    Asset Compression Rule: Explicitly configured (noCompress 'bin') to prevent Android from compressing the AI weights during APK packaging. This allows lightning-fast file extraction directly into the device RAM upon app boot.

    C++ Standard: Forced to the C++17 revised standard, which is strictly required by the underlying AI/ML libraries (CTranslate2 and SentencePiece).

📥 Setup & AI Model Instructions

The app relies on a dual-pipeline architecture utilizing two pre-trained IndicTrans2 models, which must be downloaded and placed into the project assets.

    Get Model Access:

        Visit the Adalat AI Hugging Face Repository for the CTranslate2 distil models:

            Hugging Face Repo - English to Indic

            Hugging Face Repo - Indic to English

        Log in to your Hugging Face account and accept the terms and conditions to gain access.

    Download & Place the Models:

        Engine 1 (English to Indic): Download the model files (~847 MB) and place the directory inside app/src/main/assets/indictrans2_200m/.

        Engine 2 (Indic to English): Download the corresponding reverse model files and place the directory inside app/src/main/assets/indic-eng/.

⚠️ Critical GitHub Warning:
Because the model.bin files exceed GitHub's 100MB limit, they are explicitly ignored in the .gitignore file. Never attempt to force-push the .bin files, or your commit history will lock up.
🚀 Getting Started for Contributors

    Clone the repository and open the project in Android Studio.

    Ensure you have downloaded and placed both AI model files in the proper assets/ directories as outlined above.

    Sync the project with Gradle files to build the NDK C++ toolchain.

    Build and run the app on a physical 64-bit Android device. (Note: Android Emulators frequently lack the allocated RAM and processing architecture required to host dual offline AI models simultaneously).
