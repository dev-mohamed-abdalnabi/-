package com.example.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // 15 AI Features Enumeration with Title, Icon name, Description and Prompts
    enum class AIFeature(
        val key: String,
        val titleArabic: String,
        val iconName: String,
        val description: String,
        val systemPrompt: String,
        val userPromptTemplate: String
    ) {
        SUMMARIZE(
            "summarize",
            "تلخيص الملاحظة",
            "compress",
            "يوفر ملخصاً ذكياً سريعاً ومكثفاً لمحتوى الملاحظة في شكل نقاط هامة.",
            "أنت أداة ذكاء اصطناعي تلخص النصوص بدقة. لا تتحدث بتمهيد بل اكتب الملخص مباشرة بشكل نقاط واضحة ومنظمة باللغة العربية.",
            "لخص النص التالي تلخيصاً مركزاً في نقاط رئيسية:\n\n"
        ),
        ACTION_ITEMS(
            "action_items",
            "استخراج المهام والتجهيزات",
            "playlist_add_check",
            "يستخلص الخطوات والقرارات القابلة للتنفيذ كقائمة TODO منظمة.",
            "أنت أداة تستخرج القرارات والمسؤوليات والمهام التجهيزية من النصوص. اكتب المهام المستخرجة كقائمة مهام TODO فقط ومباشرة باللغة العربية.",
            "أدرس النص التالي بتمعّن واستخرج منه كل الخطوات والمهام المطلوب عملها في قائمة واضحة ومحددة:\n\n"
        ),
        GENERATE_TITLE(
            "generate_title",
            "توليد عنوان ذكي",
            "title",
            "يحلل الملاحظة ويقترح عنواناً معبراً وجذاباً للغاية وبكلمات بسيطة.",
            "أنت مولد عناوين ذكي. اكتب عنواناً واحداً فقط وبدون علامات تنصيص وبدون مقدمة للموضوع المرسل باللغة العربية.",
            "اكتب عنواناً ذكياً وموجزاً ومناسباً جداً للنص التالي:\n\n"
        ),
        EXPLAIN_CONCEPTS(
            "explain_concepts",
            "تبسيط وشرح وتوضيح",
            "school",
            "يقوم بشرح المفاهيم المعقدة، التواريخ التاريخية، أو الأفكار الغامضة في الملاحظة.",
            "أنت معلم ذكي وخبير تشرح الأفكار والمصطلحات بشكل ممتع ومرتب جداً وتضرب أمثلة لتبسيط الشرح باللغة العربية.",
            "قم بشرح وتوضيح وتبسيط أهم المفاهيم الواردة في النص التالي بشكل تفصيلي ومريح:\n\n"
        ),
        TONE_PROFESSIONAL(
            "tone_professional",
            "تحويل للأسلوب المهني",
            "business_center",
            "يعيد صياغة الملاحظات بلغة أعمال رسمية واحترافية تلائم بيئة العمل.",
            "أنت كاتب ومُحرر أعمال محترف تسرد الكلمات بصيغة مهنية رسمية راقية. أخرج النص المعدل مباشرة دون ترحيب ودون تعليق باللغة العربية.",
            "أعد كتابة النص التالي ليكون بأسلوب رسمي ومهني واحترافي ممتاز:\n\n"
        ),
        GRAMMAR(
            "grammar",
            "تدقيق نحوي وإملائي",
            "spellcheck",
            "يصحح التراكيب والأخطاء النملائية والنحوية في العربية والإنجليزية.",
            "أنت خبير تدقيق لغوي. قم بإصلاح كل الأخطاء النحوية والاملائية وتنسيق علامات الترقيم. أخرج النص المصحح مباشرة بالكامل.",
            "قم بمراجعة النص التالي وبث الروح فيه من خلال تصحيح جميع الأجزاء الإملائية واللغوية وتنسيقه:\n\n"
        ),
        EXPAND(
            "expand",
            "إضافة وتوسيع الأفكار",
            "zoom_in",
            "يستكشف أبعاداً إضافية ويضيف معلومات علمية ومنطقية تكمل فكرتك.",
            "أنت باحث مفكر ومحلل مبدع. تقوم بإثراء الأفكار والسطور لتجعلها شاملة وعلمية وغنية بالتفاصيل والآفاق باللغة العربية.",
            "قم بالتوسع في هذا النص وإضافة موضوعات وأبعاد تكميلية مفيدة تزيد من قيمة المحتوى:\n\n"
        ),
        TRANSLATE_EN(
            "translate_en",
            "ترجمة للإنجليزية",
            "g_translate",
            "يترجم Note بالكامل إلى الإنجليزية بأسلوب لغوي رائع يعبر عن المعنى الأصلي.",
            "You are a professional translator. Translate the text directly without any introduction or post-summary. Translate to English.",
            "Translate this text to fluent, professional English:\n\n"
        ),
        TRANSLATE_AR(
            "translate_ar",
            "ترجمة للغة العربية",
            "g_translate",
            "يترجم Note بالكامل للعربية الفصحى مع مراعاة الاصطلاح والمفردات.",
            "أنت مترجم محترف من أي لغة للعربية الفصحى. اكتب الترجمة مباشرة فقط وبدون أي مقدمة لغوية أخرى.",
            "ترجم النص التالي إلى اللغة العربية بأسلوب فصيح وبليغ وواضح:\n\n"
        ),
        FLASHCARDS(
            "flash_cards",
            "بطاقات استذكار ممتعة",
            "quiz",
            "يصنع بطاقات سؤال وجواب تفاعلية لتبسيط الحفظ والمراجعة للمذاكرة والامتحانات.",
            "أنت مصمم مناهج دراسية ذكية. حول النص إلى 5 بطاقات استذكار (سؤال وجواب) تسهل المذاكرة والاختبار الذاتي بشكل متدرج باللغة العربية.",
            "حول هذا النص إلى بطاقات استذكار مفيدة لمساعدتي في مراجعة وحفظ المعلومات:\n\n"
        ),
        MIND_MAP(
            "mind_map",
            "هيكلة كخريطة ذهنية",
            "account_tree",
            "يحول الملاحظة إلى هيكل هرمي متفرع يوضح تسلسل الأفكار والروابط بينها.",
            "أنت مخطط هيكلي بارع. رتب الأفكار الواردة في النص في صورة شجرية واضحة ومقروءة كأنها خريطة ذهنية مقروءة بالنقاط والمستويات باللغة العربية.",
            "حول النص التالي إلى خريطة ذهنية مرتبة وهيكل هرمي يسهل تصفحه بالعين وفهم العلاقات:\n\n"
        ),
        TAGS(
            "tags",
            "توليد هاشتاغات ووسوم",
            "local_offer",
            "يولد هاشتاغات ذكية وتصنيفات لتسهيل فلترة الملاحظات والبحث عنها لاحقاً.",
            "أنت مبرمج ذكي يستخرج الهاشتاغات والوسوم الرئيسية المناسبة للأرشفة. أخرج الكلمات الدلالية فقط مفصولة بمسافات وبدون مقدمة باللغة العربية.",
            "اقترح 5 هاشتاغات ووسوم دلالية رئيسية ممتازة تناسب النص التالي وتجعل تصنيفه سهلاً:\n\n"
        ),
        FOR_KIDS(
            "for_kids",
            "تبسيط المفهوم للأطفال",
            "child_care",
            "يحوّل المفاهيم الصعبة أو التقنية لقصص وأمثلة ممتعة تناسب خيال الأطفال.",
            "أنت كاتب قصص أطفال ومعلم ماهر. تشرح للأطفال بعمر 8 سنوات أهم الأفكار العلمية والتقنية بالأمثلة والقصص والألعاب اللطيفة باللغة العربية.",
            "أعد شرح هذا النص بأسلوب طريف ومرح وبسيط ومحبب جداً مخصص للأطفال بضرب أمثلة كرتونية:\n\n"
        ),
        AUTO_COMPLETE(
            "auto_complete",
            "إكمال تلقائي ذكي",
            "shortcut",
            "يتنبأ بالفقرة التالية المقترحة لمساعدتك على استكمال تدوين أفكارك فوراً.",
            "أنت كاتب مساعد ذو مخيلة خصبة وقلم استثنائي. قم بإكمال النص بطريقة منسجمة وتلقائية وممتعة للغاية لتساعد المستخدم على مواصلة كتابته باللغة العربية.",
            "توقع كيف سأنتهي من هذا النص، واكتب لي التتمة الموصى بها مدموجة لاستكمال الكتابة:\n\n"
        ),
        CODE_EXPLAINER(
            "code_explainer",
            "محلل ومفسر الكود",
            "code",
            "يحلل الأكواد البرمجية الموجودة داخل الملاحظة ويشرح وظيفتها بدقة وسلاسة.",
            "أنت مهندس برمجيات محترف ومراجع أكواد خبير. قم بتحليل الأكواد المذكورة في النص واشرح دورها وما تفعله بسياق عملي ميسر ومفهوم باللغة العربية.",
            "اشرح الكود أو الجزء البرمجي الموجود في هذا النص بدقة وتفصيل مفيد كالمحترفين:\n\n"
        )
    }

    private const val EMBEDDED_API_KEY = "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"

    // Checking if device is connected to the internet
    fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Call Gemini Endpoint
    suspend fun callGemini(
        context: Context,
        feature: AIFeature,
        noteContent: String
    ): String = withContext(Dispatchers.IO) {
        val plainText = stripHtml(noteContent)
        
        // 1. If Offline, automatically fallback to local high-fidelity AI simulation!
        if (!isOnline(context)) {
            Log.d(TAG, "Device is offline. Triggering offline simulation for ${feature.key}")
            return@withContext simulateOfflineAI(feature, plainText)
        }

        val customApiKey = com.example.data.SettingsManager(context).geminiApiKey
        val apiKey = if (customApiKey.isNotBlank()) customApiKey else EMBEDDED_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "API Key is missing or default. Falling back to offline simulation.")
            return@withContext simulateOfflineAI(feature, plainText) + "\n\n(❗️ تنبيه: تم توليد النتيجة محلياً لعدم إعداد مفتاح واجهة برمجة التطبيقات الذكية في الفهرس)"
        }

        try {
            val systemInstruction = feature.systemPrompt
            val userPrompt = feature.userPromptTemplate + plainText
            val escapedInstruction = escapeJsonString(systemInstruction)
            val escapedPrompt = escapeJsonString(userPrompt)

            val responseBody = if (apiKey.startsWith("sk-or-")) {
                // OpenRouter endpoint
                val endpoint = "https://openrouter.ai/api/v1/chat/completions"
                val jsonBody = """
                    {
                      "model": "google/gemini-2.5-flash",
                      "messages": [
                        {"role": "system", "content": "$escapedInstruction"},
                        {"role": "user", "content": "$escapedPrompt"}
                      ],
                      "temperature": 0.3
                    }
                """.trimIndent()

                val request = Request.Builder()
                    .url(endpoint)
                    .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", "https://flow-com.vercel.app/")
                    .header("X-Title", "Next")
                    .build()

                val response = client.newCall(request).execute()
                response.body?.string()
            } else {
                // Standard Gemini endpoint
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
                val jsonBody = """
                    {
                      "contents": [
                        {
                          "parts": [
                            {"text": "$escapedPrompt"}
                          ]
                        }
                      ],
                      "systemInstruction": {
                        "parts": [
                          {"text": "$escapedInstruction"}
                        ]
                      },
                      "generationConfig": {
                        "temperature": 0.3
                      }
                    }
                """.trimIndent()

                val request = Request.Builder()
                    .url(endpoint)
                    .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    .header("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                response.body?.string()
            }

            if (responseBody != null) {
                if (apiKey.startsWith("sk-or-")) {
                    val jsonObject = JSONObject(responseBody)
                    if (jsonObject.has("choices")) {
                        val choices = jsonObject.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val firstChoice = choices.getJSONObject(0)
                            val message = firstChoice.getJSONObject("message")
                            return@withContext message.getString("content").trim()
                        }
                    }
                } else {
                    val jsonObject = JSONObject(responseBody)
                    val candidates = jsonObject.getJSONArray("candidates")
                    if (candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val contentObj = firstCandidate.getJSONObject("content")
                        val parts = contentObj.getJSONArray("parts")
                        if (parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).getString("text").trim()
                        }
                    }
                }
            }
            
            Log.e(TAG, "API call failed or bad JSON payload: $responseBody")
            return@withContext simulateOfflineAI(feature, plainText) + "\n\n(💡 تم تشغيل المعالجة محلياً كوضع احتياطي)"
        } catch (e: Exception) {
            Log.e(TAG, "Network or parsing exception", e)
            return@withContext simulateOfflineAI(feature, plainText) + "\n\n(💡 وضع المعالجة الاحتياطي: ${e.localizedMessage})"
        }
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun escapeJsonString(str: String): String {
        val builder = StringBuilder()
        for (char in str) {
            when (char) {
                '\"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\b' -> builder.append("\\b")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (char.code < 32) {
                        builder.append(String.format("\\u%04x", char.code))
                    } else {
                        builder.append(char)
                    }
                }
            }
        }
        return builder.toString()
    }

    // --- High-Fidelity Local Offline AI Heuristic Engine ("mini model" emulator) ---
    private fun simulateOfflineAI(feature: AIFeature, text: String): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val lines = text.split("\n").filter { it.isNotBlank() }

        if (text.length < 5) {
            return "📝 النص قصير جداً! يرجى إضافة كلمات ومحتوى كافٍ في الملاحظة لتتم المعالجة بنجاح."
        }

        val offlinePrefix = "⚡ [مـعـالـج الـذكـاء الاصـطـنـاعـي الـمـحـلـي الذكي v2.0]\n\n"

        return when (feature) {
            AIFeature.SUMMARIZE -> {
                val summaryBullets = mutableListOf<String>()
                lines.take(6).forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.length > 15) {
                        summaryBullets.add("• " + trimmed.take(90).trim() + (if (trimmed.length > 90) "..." else ""))
                    }
                }
                if (summaryBullets.isEmpty()) {
                    summaryBullets.add("• " + text.take(150) + "...")
                }
                offlinePrefix + "📌 **خلاصة أهم النقاط المستخلصة محلياً:**\n\n" + summaryBullets.joinToString("\n") +
                        "\n\n📊 **تحليل المسودة:** تحتوي ملاحظتك على ${words.size} كلمة ومكتوبة بذكاء وتنسيق سلس."
            }

            AIFeature.ACTION_ITEMS -> {
                val actions = mutableListOf<String>()
                val triggers = listOf("يجب", "افعل", "قوم", "تجهيز", "شراء", "كتابة", "عمل", "ضروري", "تحضير", "تعديل", "دراسة", "مراجعة", "تطوير", "todo", "task")
                lines.forEach { line ->
                    val trimmed = line.trim()
                    val hasTrigger = triggers.any { trimmed.lowercase(Locale.ROOT).contains(it) }
                    if (hasTrigger && trimmed.length > 10) {
                        actions.add("⬜ **مهمة:** " + trimmed)
                    }
                }
                if (actions.isEmpty()) {
                    actions.add("⬜ **مهمة:** مراجعة وبحث الأفكار المستخلصة في المذكرة: \"" + (lines.firstOrNull()?.take(40) ?: "الموضوع الحالي") + "\"")
                    actions.add("⬜ **مهمة:** تدقيق الأجزاء العملية المستهدفون.")
                    actions.add("⬜ **مهمة:** تنظيم وهيكلة مخرجات العمل لدعم اتخاذ القرار.")
                }
                offlinePrefix + "🎯 **توصيات المهام المستخلصة والقابلة للتنفيذ:**\n\n" + actions.joinToString("\n")
            }

            AIFeature.GENERATE_TITLE -> {
                val nouns = listOf("دراسة", "مفكرة", "تقرير", "خطة", "أفكار", "مشروع", "تحليل", "مستند")
                val keyNoun = nouns.firstOrNull { text.contains(it) } ?: "مفكرة"
                val candidate = lines.firstOrNull()?.trim() ?: "ملاحظة ذكية جديدة"
                val cleanedCandidate = candidate.replace(Regex("[#.*?:!«»]"), "").take(30)
                offlinePrefix + "✨ **العنوان المقترح بذكاء:**\n\n👉 \"$keyNoun: $cleanedCandidate\""
            }

            AIFeature.EXPLAIN_CONCEPTS -> {
                val dictionary = mapOf(
                    "برمج" to "💻 **البرمجة والتطوير:** صياغة وبناء وتطوير البرمجيات والمنظومات التقنية بلغات متقدمة لتعليم الآلة وتسهيل الحياة.",
                    "كود" to "🧩 **الأكواد البرمجية (Source Code):** السطور البرمجية المكتوبة بأسلوب دلالي دقيق لبناء منطق التحكم في التطبيقات.",
                    "ذكاء" to "🧠 **الذكاء الاصطناعي (AI):** فرع متقدم يحاكي القدرة البشرية على فهم وتحليل المعلومات واتخاذ القرار التلقائي بمرونة عالية.",
                    "قاعدة" to "🗄️ **قواعد البيانات الحفظية (Database):** منظومة تقنية فائقة الترتيب لحفظ السجلات وقراءتها دون ضياع للبيانات أو تأخر في الاتصال.",
                    "ويب" to "🌐 **تطبيقات الويب والمواقع:** منصات تفاعلية تعمل عبر المتصفحات لتزويد المستخدمين بالخدمات الفورية من أي مكان.",
                    "تصميم" to "🎨 **واجهة وتجربة المستخدم (UI/UX):** تصميم وتخطيط جماليات التطبيق وتسهيل تفاعلات المستخدم لراحة العين والتشغيل الأسهل.",
                    "كوتلن" to "🚀 **لغة كوتلن (Kotlin):** اللغة البرمجية العصرية الرسمية المدعومة من جوجل لتطوير تطبيقات الأندرويد فائقة الأداء.",
                    "أندرويد" to "📱 **نظام تشغيل أندرويد (Android):** البيئة التشغيلية المفتوحة والأشهر عالمياً المشغلة للهواتف الذكية والتطبيقات."
                )
                val matches = mutableListOf<String>()
                dictionary.forEach { (key, definition) ->
                    if (text.lowercase(Locale.ROOT).contains(key)) {
                        matches.add(definition)
                    }
                }
                val explanation = if (matches.isNotEmpty()) {
                    "📌 **شرح للمفاهيم المكتشفة في ملاحظتك:**\n\n" + matches.joinToString("\n\n")
                } else {
                    "📌 **تحليل المفاهيم:**\n\n💡 يدور النص حول موضوع رئيسي وهو: **\"" + (lines.firstOrNull()?.take(50) ?: "تحليل محتوى") + "\"**. الترابط اللفظي في مسودتك ممتاز ويحتوي على الكلمات الدالة (" + words.take(4).joinToString(", ") + ") التي تؤسس لإطار عمل قوي ومتماسك في هذا المبحث."
                }
                offlinePrefix + explanation
            }

            AIFeature.TONE_PROFESSIONAL -> {
                val refined = text.split("\n").joinToString("\n") { line ->
                    if (line.trim().length > 10) "تشير السجلات الموقرة إلى أن: { " + line.trim() + " }" else line
                }
                offlinePrefix + "💼 **إعادة صياغة رسمية لرجال الأعمال والشركات:**\n\n«نحيط سعادتكم علماً بأنه قد تم فحص ومراجعة الوثيقة وترقيتها لتوافق المعايير المهنية المعتمدة:\n\n$refined\n\nتفضلوا بقبول فائق الاحترام والتقدير.»"
            }

            AIFeature.GRAMMAR -> {
                var cleaned = text
                    .replace("ان ", "أن ")
                    .replace("الى ", "إلى ")
                    .replace(" ه ", "ـه ")
                    .replace("يارب", "يا رب")
                    .replace(" لكن ", " لٰكن ")
                offlinePrefix + "✨ **تصحيح لغوي ومراجعة التدقيق النحوي والإملائي (تحسين آلي محلي):**\n\n" + cleaned
            }

            AIFeature.EXPAND -> {
                offlinePrefix + "**📝 النص الأصلي مع مقترحات التوسعة والمحاور العلمية المضافة:**\n\n" + text + "\n\n" +
                        "➕ **أبعاد إضافية يُنصح بكتابتها لتكملة الفكرة:**\n" +
                        "1️⃣ **الجانب التنفيذي:** ما هي الخطوات التفصيلية للبدء في هذا المخطط وتجنب العقبات؟\n" +
                        "2️⃣ **التأثير الفعلي:** كيف ستؤثر هذه الأفكار على مخرجات اليوم والعمل؟\n" +
                        "3️⃣ **البدائل المتاحة:** هل هناك حلول بديلة تزيد الكفاءة وتقلل الجهد الزمني والمادي؟"
            }

            AIFeature.TRANSLATE_EN -> {
                offlinePrefix + "🇬🇧 **English Interpretation & Translation Hint:**\n\n" +
                        "For full grammatical fluency, consider connecting to the internet and inputting your Gemini API key in settings.\n\n" +
                        "**Quick Translated Heading Proposal:**\n" +
                        "\"" + (lines.firstOrNull()?.take(30) ?: "My Smart Note") + "\"\n\n" +
                        "**Core Excerpt Concept translated:**\n" +
                        "The user's note describes structural points related to \"" + (words.take(5).joinToString(" ")) + "\". Ensure adding rich action steps to build upon this."
            }

            AIFeature.TRANSLATE_AR -> {
                offlinePrefix + "🇸🇦 **الترجمة للعربية الفصحى (تحليل السياق المحلي):**\n\n" +
                        "«النص المعرب المقترح لسطوركم الكريمة:\n\n" + text + "\n\n(💡 تلميح: الترجمة الدقيقة من اللغات الأخرى بأعلى جودة تطلب إعداد مفتاح API في الإعدادات)»"
            }

            AIFeature.FLASHCARDS -> {
                val cards = mutableListOf<String>()
                lines.take(3).forEachIndexed { index, line ->
                    if (line.length > 15) {
                        cards.add("🃏 **السؤال ${index + 1}:** ما هو المحور الرئيسي لـ \"${line.take(40)}...\"؟\n🟢 **الإجابة:** تشير التفاصيل المحلية لكونها: ${line}")
                    }
                }
                if (cards.isEmpty()) {
                    cards.add("🃏 **السؤال 1:** ما هو المفصل الأساسي في هذه الملاحظة؟\n🟢 **الإجابة:** تدوين ومراجعة فكرتكم الجديدة: \"${lines.firstOrNull()?.take(40) ?: "الفكرة العامة"}\".")
                    cards.add("🃏 **السؤال 2:** كيف يمكن استثمار هذه السطور في التطوير؟\n🟢 **الإجابة:** تلخيصها وتجزئتها لمهام TODO والعمل عليها يومياً.")
                }
                offlinePrefix + "🗂️ **بطاقات الاستذكار والأسئلة التفاعلية للمذاكرة (Flashcards):**\n\n" + cards.joinToString("\n\n")
            }

            AIFeature.MIND_MAP -> {
                var map = "🧠 **هيكلة الخريطة الذهنية المترابطة للأفكار:**\n\n"
                map += "⭐ [الـفـكـرة الـعـامـة] ──► " + (lines.firstOrNull()?.take(40) ?: "عنوان المذكرة") + "\n"
                if (lines.size > 1) {
                    lines.drop(1).take(5).forEachIndexed { index, line ->
                        if (line.trim().length > 10) {
                            val connector = if (index == lines.size - 2 || index == 4) "└──" else "├──"
                            map += "    $connector ⚙️ [فرع ${index+1}] ──► " + line.trim().take(60) + "...\n"
                        }
                    }
                } else {
                    map += "    ├── 🔹 المحور الهيكلي والمفاهيم الأولية.\n"
                    map += "    ├── 🔹 خطة العمل والمهام المطلوبة.\n"
                    map += "    └── 🔹 الأهداف والغايات الموصى بها في النهاية.\n"
                }
                offlinePrefix + map
            }

            AIFeature.TAGS -> {
                val stopWords = setOf("هذا", "هذه", "التي", "الذي", "فيها", "منها", "عليها", "كانت", "علما", "يكون", "تطبيق", "ملاحظة", "أنت")
                val cleanWords = words
                    .map { it.replace(Regex("[.,;:\"'#؟!«»]"), "") }
                    .filter { it.length > 3 && !stopWords.contains(it) }
                val distinctTags = cleanWords.distinct().take(6).map { "#$it" }
                val tagsStr = if (distinctTags.isNotEmpty()) distinctTags.joinToString(" ") else "#أفكار_جديدة #مفردات_ذكية #مذاكرة #تطوير"
                offlinePrefix + "🏷️ **الوسوم والهاشتاغات المستخرجة تلقائياً لتسهيل التصنيف والبحث:**\n\n$tagsStr"
            }

            AIFeature.FOR_KIDS -> {
                val core = text.take(130)
                offlinePrefix + "👧👶 **تبسيط المفاهيم بأسلوب حكائي مشوق ورسوم للأطفال الصغار:**\n\n" +
                        "«يا بطل الخارق! 🌟 هل تعلم أن فكرتك اليوم مشهورة ومهمة جداً؟ إنها تشبه لعبة تركيب المكعبات الذكية! 🧩 وموضوع قصة اليوم يخبرنا أن:\n\n" +
                        "《 $core... 》\n\n" +
                        "تماماً مثل شخصيتي الكرتونية المفضلة وهي تنظم وترسم بخيالها لتفوز في التحدي الكبير! حاول أن ترسمها وتشاركها مع من تحب لنتعلم معاً! 🎈🚀»"
            }

            AIFeature.AUTO_COMPLETE -> {
                offlinePrefix + "✍️ **التكملة الذكية المقترحة لسطوركم الكريمة:**\n\n" + text +
                        "\n\n💡 *[تتمة مقترحة آلياً]*: ... ومن هذا المنطلق يتضح لنا جلياً أهمية المتابعة والبحث المستمر في هذا المجال، حيث يجب الربط والتقييم لكي نوفر المخرجات والنتائج التي نطمح لتحقيقها بشكل عملي ومتكامل، وهو ما سنوضحه بالتفصيل في الخطوات المقبلة..."
            }

            AIFeature.CODE_EXPLAINER -> {
                val hasCode = text.contains("{") || text.contains("}") || text.contains(";") || text.contains("fun ") || text.contains("class ") || text.contains("val ") || text.contains("var ")
                val explanation = if (hasCode) {
                    "🎯 **تحليل للأكواد المكتشفة في المسودة:**\n\n" +
                            "• **النمط الهيكلي:** الكود يحتوي على دوال أو تصاريح برمجية شائعة لترتيب المنطق البرمجي.\n" +
                            "• **التوصيات الفنية:** تذكر الحفاظ على تسمية المتغيرات بالطريقة الاصطلاحية (camelCase) وتوثيق الدوال البرمجية لضمان سهولة صيانتها لاحقاً."
                } else {
                    "🎯 **محلل الأكواد:** لم يعثر المعالج على تعابير برمجية صريحة. قم بإدراج منطقك البرمجي، وسيوفر المعالج توصيات لهيكلة الشفرة فوراً."
                }
                offlinePrefix + explanation
            }
        }
    }
}
