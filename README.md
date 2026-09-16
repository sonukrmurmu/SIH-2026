Markdown

# Indic Translation Android App (Santali & Regional Focus)

An Android application designed to perform offline/edge translation using a custom CTranslate2 IndicTrans2 model, optimized with a local fallback dictionary and native C++ integration.

---

## 📂 Project Directory Structure

```text
/translator/
├── app/src/main/
│   ├── cpp/                       # C++ native engine & CMake configuration
│   │   ├── CMakeLists.txt         # Links native libraries and UI components
│   │   └── ...                    # C++ source files bridging UI text to the AI model
│   ├── java/ or kotlin/           # App source code (UI & logic handlers)
│   │   └── .../MainActivity.kt    # Handles UI input, passes data to C++, and updates views
│   │   └── .../DictionaryDb.kt    # Post-processes/analyzes dictionary overrides for model corrections
│   ├── res/                       # App resources
│   │   ├── font/                  # Custom Santali font files
│   │   └── layout/
│   │       └── activity_main.xml  # Main UI layout (Assigned to Anamika)
│   ├── assets/                    # Local storage for assets
│   │   ├── dictionary.db          # SQLite dictionary database (1,000+ words for edge corrections)
│   │   └── indic_trans/           # CTranslate2 AI model directory
│   └── AndroidManifest.xml        # App permissions and configurations
└── build.gradle.kts               # Global/App-level build configurations

🧠 Core Architecture & Components

    Native Layer (C++):
    Located in app/src/main/cpp/, this layer handles the heavy lifting of passing text data cleanly from the Android UI into the CTranslate2 model runtime.

    Kotlin Logic (MainActivity.kt):
    Manages user input from the UI, triggers the C++ translation engine, receives the output, and updates the display screen.

    Dictionary Correction Kit (DictionaryDb):
    An intelligent post-processing layer that cross-references a local database (1,000 words) to correct occasional model inaccuracies for specific terminology.

    UI Layout (activity_main.xml):
    Located under app/src/main/res/layout/. Note: UI design and layout implementation are handled by Anamika.

⚙️ Build & Configuration Highlights (build.gradle.kts)

Key configurations implemented in the project build files:

    Architecture Targeting: Configured strictly for 64-bit libraries to prevent compatibility errors associated with 32-bit binaries.

    Asset Compression Rule: Configured to prevent compression of the AI model files during APK packaging.

    C++ Standard: Forced to the C++17 revised standard, which is required by the underlying AI/ML libraries.

📥 Setup & AI Model Instructions

The app relies on a pre-trained IndicTrans2 model which must be downloaded and placed into the project assets.

    Get Model Access:

        Visit the Hugging Face Repository.

        Log in to your Hugging Face account and accept the terms and conditions to gain access.

    Download & Place the Model:

        Download the model files (~847 MB).

        Place the model directory inside app/src/main/assets/indic_trans/.

🚀 Getting Started for Contributors

    Clone the repository and open the project in Android Studio.

    Ensure you have placed the downloaded model files in the proper assets/ directory as outlined above.

    Sync project with Gradle files.

    Build and run the app on an emulator or a physical 64-bit Android device.
