package com.tmm.mobile.ui.screens.translationsettings

enum class SourceLanguage(val code: String, val label: String, val badge: String = "") {
    Auto("auto", "Détection automatique"),
    Japanese("JPN", "Japonais", "Manga"),
    Korean("KOR", "Coréen", "Webtoon"),
    Chinese("CHS", "Chinois simplifié");

    val displaySummary: String get() = if (badge.isNotEmpty()) "$label · $badge" else label
}

enum class TargetLanguage(val code: String, val label: String) {
    French("FRA", "Français"),
    English("ENG", "Anglais"),
    Spanish("SPA", "Espagnol"),
    German("DEU", "Allemand");

    val displaySummary: String get() = label
}

enum class TranslationProvider(val id: String, val label: String, val badge: String = "") {
    Gemini("gemini", "Google Gemini 2.5 Flash", "Recommandé"),
    OpenAi("openai", "OpenAI GPT-4.1 mini"),
    Claude("claude", "Anthropic Claude Sonnet 5"),
    DeepL("deepl", "DeepL API"),
    Groq("groq", "Groq GPT-OSS 120B"),
    OpenRouter("openrouter", "OpenRouter", "Hy-MT2"),
    TokenHub("tokenhub", "Tencent TokenHub", "Hy-MT2 Plus"),
    Offline("offline", "Hors ligne", "Sugoi / M2M100"),
    LocalLlm("local_llm", "LLM local", "Ollama sur le PC");

    val displaySummary: String get() = if (badge.isNotEmpty()) "$label · $badge" else label
}

enum class BubbleDetector(val id: String, val label: String, val badge: String = "") {
    Default("default", "Comic-Text-Detector", "Automatique"),
    Ctd("ctd", "CTD explicite"),
    Paddle("paddle", "PaddleOCR Detector");

    val displaySummary: String get() = if (badge.isNotEmpty()) "$label · $badge" else label
}

enum class PreferredOcr(val id: String, val label: String, val badge: String = "") {
    Auto("auto", "Automatique", "Manga-OCR + PaddleOCR"),
    MangaOcr("mocr", "Manga-OCR"),
    Px48("48px", "48px multi-langues"),
    Px32("32px", "32px standard");

    val displaySummary: String get() = if (badge.isNotEmpty()) "$label · $badge" else label
}

enum class Inpainter(val id: String, val label: String, val badge: String = "") {
    LamaLarge("lama_large", "LaMa Large"),
    LamaMpe("lama_mpe", "LaMa MPE"),
    StableDiffusion("sd", "Stable Diffusion"),
    None("none", "Aucun effacement");

    val displaySummary: String get() = if (badge.isNotEmpty()) "$label · $badge" else label
}

enum class InpaintingSize(val sizePx: Int, val label: String, val badge: String = "") {
    Size1024(1024, "1024 px", "Plus rapide"),
    Size1280(1280, "1280 px", "Recommandé"),
    Size1800(1800, "1800 px", "HD"),
    Size2048(2048, "2048 px", "Maximum");

    val displaySummary: String get() = if (badge.isNotEmpty()) "$label · $badge" else label
}

enum class TranslationFont(val fontName: String, val label: String) {
    AnimeAce("Anime Ace", "Anime Ace"),
    MangaTemple("Manga Temple", "Manga Temple");

    val displaySummary: String get() = label
}

enum class ActiveSheet {
    SourceLanguage,
    TargetLanguage,
    TranslationProvider,
    BubbleDetector,
    PreferredOcr,
    Inpainter,
    InpaintingSize,
    Font,
}

enum class TranslationSyncStatus {
    Synced,
    Sending,
    Applied,
}

data class TranslationSettingsState(
    val sourceLanguage: SourceLanguage = SourceLanguage.Auto,
    val targetLanguage: TargetLanguage = TargetLanguage.French,
    val translationProvider: TranslationProvider = TranslationProvider.Gemini,
    val autoFailoverToLocalLlm: Boolean = true,
    val autoVerifyAfterTranslate: Boolean = true,
    val chapterConcurrency: Int = 4,
    val concurrency: Int = 2,
    val pileSize: Int = 3,
    val useGpu: Boolean = true,
    val detector: BubbleDetector = BubbleDetector.Default,
    val preferredOcr: PreferredOcr = PreferredOcr.Auto,
    val inpainter: Inpainter = Inpainter.LamaLarge,
    val inpaintingSize: InpaintingSize = InpaintingSize.Size1280,
    val font: TranslationFont = TranslationFont.AnimeAce,
    val isAdvancedExpanded: Boolean = false,
    val activeSheet: ActiveSheet? = null,
    val syncStatus: TranslationSyncStatus = TranslationSyncStatus.Synced,
)

sealed interface TranslationSettingsEvent {
    data class SelectSourceLanguage(val value: SourceLanguage) : TranslationSettingsEvent
    data class SelectTargetLanguage(val value: TargetLanguage) : TranslationSettingsEvent
    data class SelectTranslationProvider(val value: TranslationProvider) : TranslationSettingsEvent
    data class SetAutoFailover(val value: Boolean) : TranslationSettingsEvent
    data class SetAutoVerify(val value: Boolean) : TranslationSettingsEvent
    data class SetChapterConcurrency(val value: Int) : TranslationSettingsEvent
    data class SetConcurrency(val value: Int) : TranslationSettingsEvent
    data class SetPileSize(val value: Int) : TranslationSettingsEvent
    data class SetUseGpu(val value: Boolean) : TranslationSettingsEvent
    data class SelectDetector(val value: BubbleDetector) : TranslationSettingsEvent
    data class SelectPreferredOcr(val value: PreferredOcr) : TranslationSettingsEvent
    data class SelectInpainter(val value: Inpainter) : TranslationSettingsEvent
    data class SelectInpaintingSize(val value: InpaintingSize) : TranslationSettingsEvent
    data class SelectFont(val value: TranslationFont) : TranslationSettingsEvent
    data object ToggleAdvanced : TranslationSettingsEvent
    data class OpenSheet(val sheet: ActiveSheet) : TranslationSettingsEvent
    data object CloseSheet : TranslationSettingsEvent
}
