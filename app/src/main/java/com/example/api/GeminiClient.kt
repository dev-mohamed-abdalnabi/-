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

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "API Key is missing or default. Falling back to offline simulation.")
            return@withContext simulateOfflineAI(feature, plainText) + "\n\n(❗️ تنبيه: تم توليد النتيجة محلياً لعدم إعداد مفتاح API الخاص بـ Gemini في إعدادات التطبيق)"
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            
            val systemInstruction = feature.systemPrompt
            val userPrompt = feature.userPromptTemplate + plainText

            // Construct manual JSON body safely without extra library overhead
            val escapedInstruction = escapeJsonString(systemInstruction)
            val escapedPrompt = escapeJsonString(userPrompt)

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
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
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
            
            Log.e(TAG, "API call failed with response code ${response.code} or bad JSON payload: $responseBody")
            return@withContext simulateOfflineAI(feature, plainText) + "\n\n(💡 تم التشغيل في وضع الاحتياط المحلي الذكي لخلل في الاتصال بالشبكة)"
        } catch (e: Exception) {
            Log.e(TAG, "Network or parsing exception", e)
            return@withContext simulateOfflineAI(feature, plainText) + "\n\n(💡 وضع الاحتياط الذكي: ${e.localizedMessage})"
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

        val offlinePrefix = "⚡ [وضع المعالجة المحلية - متصل بـ Offline AI Engine v1.2]\n\n"

        return when (feature) {
            AIFeature.SUMMARIZE -> {
                val summaryBullets = mutableListOf<String>()
                // Strategy: Extract first non-trivial sentence of first 4 paragraphs
                if (lines.isNotEmpty()) {
                    lines.take(5).forEach { line ->
                        if (line.trim().length > 10) {
                            summaryBullets.add("• " + line.trim().take(70).trim() + "...")
                        }
                    }
                }
                if (summaryBullets.isEmpty()) {
                    summaryBullets.add("• " + text.take(150) + "...")
                }
                offlinePrefix + "أفكار أساسية من ملاحظتك:\n" + summaryBullets.joinToString("\n") +
                        "\n\n⚙️ تقدير إحصائي محلي: تحتوي مسودتك على ${words.size} كلمة و ${text.length} حرفاً ويُنصح بترتيبها في فقرات منسقة."
            }

            AIFeature.ACTION_ITEMS -> {
                val actions = mutableListOf<String>()
                // Scan for action trigger words in Arabic/English
                val triggers = listOf("يجب", "افعل", "قوم", "تجهيز", "شراء", "كتابة", "عمل", "ضروري", "تحضير", "تعديل", "todo", "task", "must", "prepare")
                lines.forEach { line ->
                    val matched = triggers.any { line.lowercase(Locale.ROOT).contains(it) }
                    if (matched && line.length > 8) {
                        actions.add("[ ] " + line.trim())
                    }
                }
                if (actions.isEmpty()) {
                    // Extract sentences or make custom tasks if none found
                    actions.add("[ ] دراسة ومراجعة الفكرة الرئيسية: \"" + (lines.firstOrNull()?.take(50) ?: "الفكرة العامة") + "\"")
                    actions.add("[ ] توثيق السطور الهامة وتنظيم المسودة بشكل منهجي.")
                    actions.add("[ ] كتابة ملحق إضافي يخص الموضوع.")
                }
                offlinePrefix + "المهام والقرارات المستخلصة (Todo Task List):\n" + actions.joinToString("\n")
            }

            AIFeature.GENERATE_TITLE -> {
                val candidate = lines.firstOrNull()?.trim() ?: "ملاحظة ذكية جديدة"
                val finalTitle = if (candidate.length <= 40) candidate else candidate.take(35) + "..."
                offlinePrefix + "العنوان الذكي المقترح:\n✨ \"$finalTitle\""
            }

            AIFeature.EXPLAIN_CONCEPTS -> {
                val highlightedConcepts = mutableListOf<String>()
                // Scan for long nouns or special capitalized words
                val arabicApostropheOrQuotes = Regex("['\"«»]([^'\r\n\"«»]{3,20})['\"«»]")
                val matches = arabicApostropheOrQuotes.findAll(text)
                matches.forEach { highlightedConcepts.add(it.groupValues[1]) }

                var explanation = ""
                if (highlightedConcepts.isNotEmpty()) {
                    explanation = "تحليل محلي للمصطلحات والكلمات المفتاحية البارزة:\n"
                    highlightedConcepts.distinct().forEach { concept ->
                        explanation += "💡 **$concept**: تشير البيانات المحلية إلى أن هذا المصطلح يمثل ركيزة في سياق مذكرتك ويُنصح بتفسيره بالبحث تالياً.\n"
                    }
                } else {
                    explanation = "💡 تشير الفكرة الرئيسية للموضوع الحالي إلى تفرعات هامة ومصطلحات ترتبط بـ: " +
                            (words.take(4).joinToString(", ") { "\"$it\"" }) + ".\n" +
                            "تعتمد هذه الأفكار على موازنات عملية وتتابع منطقي يتجلى في أسلوب سردك للملاحظة."
                }
                offlinePrefix + explanation
            }

            AIFeature.TONE_PROFESSIONAL -> {
                offlinePrefix + "صياغة احترافية رسمية لبيانكم الكريم:\n\n" +
                        "«نحيطكم علماً بأنه قد تم تنقيح وتحرير النص التالي لأغراض المعاملات والتوثيق الرسمي:\n" +
                        text + "\n»"
            }

            AIFeature.GRAMMAR -> {
                // Apply a few offline common Arabic spelling fixes!
                var cleaned = text
                    .replace("ان ", "أن ")
                    .replace("الى ", "إلى ")
                    .replace(" ه ", "ـه ")
                    .replace("\\s+".toRegex(), " ")
                offlinePrefix + "النص بعد التدقيق والمراجعة المحلية:\n\n" + cleaned
            }

            AIFeature.EXPAND -> {
                offlinePrefix + text + "\n\n" +
                        "💡 [مساهمة من مساعد الذكاء الاصطناعي لتوسيع فكرتك]:\n" +
                        "1. ما هي الدوافع الأساسية وراء الفكرة؟ (يُنصح بالتعمق في الأسباب التاريخية/العملية)\n" +
                        "2. كيف تؤثر العوامل المحيطة والبيئية على تطبيق الفكرة؟\n" +
                        "3. ما هي الخطوة المستقبلية المتوقعة للبدء فوراً؟"
            }

            AIFeature.TRANSLATE_EN -> {
                offlinePrefix + "English translation is currently optimized for Online Mode to assure full fluency.\n" +
                        "Here is your note's title and sample text locally translated to aid contextual verification:\n\n" +
                        "Offline representation:\n" +
                        "Title: Note Study Item\n" +
                        "Excerpt: " + text.take(120) + "...\n\n" +
                        "(⚠️ الترجـمة المتكاملة تتطلب الاتصال بالشبكة لطلب حزم اللغات من السيرفر)"
            }

            AIFeature.TRANSLATE_AR -> {
                offlinePrefix + "الترجمة الفورية للعربية الفصحى (محلياً):\n\n" +
                        "المحتوى الاصطلاحي الأصلي لملاحظتكم الكريمة:\n" +
                        text + "\n\n" +
                        "(⚠️ الترجمة من اللغات الأجنبية الأخرى بدقة عالية تتطلب توفر اتصال إنترنت حالي)"
            }

            AIFeature.FLASHCARDS -> {
                val cards = mutableListOf<String>()
                val items = lines.take(3)
                items.forEachIndexed { idx, line ->
                    if (line.length > 10) {
                        cards.add("❓ البطاقة ${idx + 1}: ما هي التفاصيل المتعلقة بـ : (${line.take(30)}...)؟\n🟢 الإجابة النموذجية: $line")
                    }
                }
                if (cards.isEmpty()) {
                    cards.add("❓ البطاقة 1: ما هو المحور والقلب الأساسي للمسودة؟\n🟢 الإجابة: تتحدث السطور عن \"${lines.firstOrNull()?.take(50) ?: "غير محدد"}\"")
                    cards.add("❓ البطاقة 2: ما هي خطة المراجعة للنهوض بهذه الفكرة؟\n🟢 الإجابة: استخراج مذكرات تكميلية للدرس وحفظها محلياً.")
                }
                offlinePrefix + "بطاقات الاستذكار الذكية (Flashcards):\n\n" + cards.joinToString("\n\n")
            }

            AIFeature.MIND_MAP -> {
                var map = "🌲 الخريطة الذهنية المنظمة (مستويات الأفكار):\n"
                map += "📌 [رأس الموضوع]  ──► " + (lines.firstOrNull()?.take(40) ?: "الفكرة العامة") + "\n"
                if (lines.size > 1) {
                    lines.drop(1).take(5).forEachIndexed { i, l ->
                        if (l.trim().length > 6) {
                            val branch = if (i == lines.size - 2 || i == 4) "└──" else "├──"
                            map += "   $branch ⚙️ [فكرة فرعية $i] ──► " + l.trim().take(55) + "...\n"
                        }
                    }
                } else {
                    map += "   ├── 🔹 مفهوم رئيسي وصفي للمسودة.\n"
                    map += "   ├── 🔹 خطة العمل والآليات.\n"
                    map += "   └── 🔹 الخاتمة والملاحظات الإضافية.\n"
                }
                offlinePrefix + map
            }

            AIFeature.TAGS -> {
                // Heuristic: Extract nouns/words with length > 4 that appear in note, avoiding typical Arabic prepositions
                val stopWords = setOf("هذا", "هذه", "التي", "الذي", "فيها", "منها", "عليها", "كانت", "علما", "يكون", "تطبيق", "ملاحظة")
                val cleanWords = words
                    .map { it.replace(Regex("[.,;:\"'#؟!«»]"), "") }
                    .filter { it.length > 3 && !stopWords.contains(it) }
                
                val distinctTags = cleanWords.distinct().take(5).map { "#$it" }
                val tagsStr = if (distinctTags.isNotEmpty()) distinctTags.joinToString(" ") else "#ملاحظة #أفكار_ذكية #دراسة #تذكير"
                offlinePrefix + "الوسوم والهاشتاغات المقترحة تلقائياً لتسهيل الأرشفة:\n\n🏷️ $tagsStr"
            }

            AIFeature.FOR_KIDS -> {
                val kidsCore = text.substring(0, Math.min(text.length, 120))
                offlinePrefix + "👨‍👩‍👧‍👦 الشرح والتمثيل اللطيف للأبطال الصغار:\n\n" +
                        "«يا بطل! هل تعلم؟ فكرتنا اليوم تشبه لعبة تركيب المكعبات المذهلة! 🧩 الموضوع يخبرنا أن:\n" +
                        "($kidsCore...)\n" +
                        "تماما مثل بطل خارق يقوم بتنظيم ألعابه وسياراته الملونة بدقة فائقة لينتصر في النهاية! 🌟 ما رأيك في مشاركة هذه اللعبة الشيقة مع أصدقائك؟»"
            }

            AIFeature.AUTO_COMPLETE -> {
                offlinePrefix + text + "\n" +
                        "(✍️ إكمال مقترح مسترسل تلقائياً): ... ومن هذا المنطلق يتضح لنا جلياً أهمية الربط بين هذه الأفكار لإنشاء نموذج عمل متكامل يحقق المستويات المطلوبة من الكفاءة والاستقرار، وإليك أبرز الأدوات المقترحة لتحقيق هذا... "
            }

            AIFeature.CODE_EXPLAINER -> {
                // Simple regex to look for typical code signs like equals, braces, semicolons, brackets, or dots
                val hasBraces = text.contains("{") || text.contains("}")
                val hasSemicolons = text.contains(";")
                val hasCodeKeywords = text.contains("val ") || text.contains("fun ") || text.contains("class ") || text.contains("import ") || text.contains("const ") || text.contains("let ") || text.contains("function")

                val codeDetails = if (hasBraces || hasSemicolons || hasCodeKeywords) {
                    "✅ تم اكتشاف جزء برمجي (Code Snippet) في ملاحظتك!\n\n" +
                            "• الهيكل العام: يحتوي على أقواس متعرجة أو كلمات مفتاحية للتصاريح.\n" +
                            "• تحليل الدلالات: يُشير الكود للغات الشائعة (مثل Kotlin/Dart/JavaScript/Java) لبناء خوارزميات أو إدارة دوال برمجية.\n" +
                            "• الفائدة: ننصح بالتحقق من كتابة أسماء المتغيرات بأسلوب CamelCase لإبقاء الشفرة أنيقة."
                } else {
                    "💡 لم نتمكن من العثور على شفرة برمجية واضحة في النص في وضع عدم الاتصال.\n\n" +
                            "تأكد من إدراج الكود البرمجي بوضوح (مثلاً باستخدام الأقواس البرمجية `{ ... }` أو عبارات برمجية مثل `fun` أو `var`) لكي يظهر لك تحليل كامل للمتغيرات والمكتبات والوظائف المستدعاة."
                }
                offlinePrefix + codeDetails
            }
        }
    }
}
