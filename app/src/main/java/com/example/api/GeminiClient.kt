package com.example.api

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val tools: List<JsonObject>? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList()
)

@Serializable
data class Candidate(
    val content: Content? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

object GeminiClient {
    
    enum class AIFeature(val titleArabic: String, val promptPrefix: String, val descriptionArabic: String) {
        SUMMARIZE(
            "تلخيص الملاحظة",
            "قم بتلخيص النص التالي بأسلوب نقاط واضحة ومباشرة:",
            "يقوم بتلخيص الأفكار والمحتوى المطول في نقاط مكثفة وقابلة للاستيعاب السريع."
        ),
        PROOFREAD(
            "تدقيق صياغة لغوية هجائية",
            "قم بمراجعة النص التالي وصحح الأخطاء النحوية أو الإملائية وحسّن الصياغة مع الحفاظ التام على المعنى والقصد:",
            "يحدد الأخطاء اللغوية والإملائية ويعيد الصياغة اللغوية المناسبة."
        ),
        FORMAT_MARKDOWN(
            "تنسيق الملاحظة Markdown",
            "قم بإعادة تنسيق النص التالي باستخدام وسوم Markdown لتتحول إلى عناوين، قوائم نصية، مقتبسات، وتسطيرات جميلة تثير الاهتمام:",
            "ينسق النصوص العادية إلى تركيبات بصرية رصينة وجذابة."
        ),
        EXTRACT_TODOS(
            "استخراج قائمة المهام",
            "استخلص قائمة المهام وخطوات العمل والمواعيد والموضوعات الواجب متابعتها من النص التالي، واعرضها في قائمة تفقد (Checkbox list) واضحة:",
            "يستكشف النص بحثاً عن بنود العمل والمواعيد ويحولها لقائمة Checklist تفاعلية."
        ),
        EXPLAIN_COMPLEX(
            "شرح وتبسيط الأفكار",
            "قم بشرح وتبسيط وتوضيح محاور النص التالي ليكون مفهوماً بشكل بديهي وسهل لغير المتخصصين:",
            "يقوم بالشرح العميق للمفهوم المطروح بأسلوب سردي مبسط."
        ),
        BRAINSTORM(
            "توليد العصف الذهني",
            "قدم اقتراحات مبتكرة، إضافات وعناوين جانبية تثري محتوى النص التالي وتفتح آفاقاً جديدة للتطوير:",
            "يقترح موضوعات وأفكاراً جديدة متعلقة بالنص لمساعدتك في توسيع أفقك."
        ),
        TRANSLATE_EN(
            "ترجمة للإنجليزية",
            "قم بترجمة النص التالي باحترافية وسلاسة وبراعة إلى اللغة الإنجليزية:",
            "يترجم الملاحظات بدقة عالية وسياق مفهوم إلى الإنجليزية."
        ),
        REPHRASE_FORMAL(
            "صياغة بأسلوب رسمي",
            "أعد كتابة النص التالي بأسلوب رسمي، احترافي، بليغ ومناسب للمراسلات المهنية وعالم الأعمال:",
            "يعيد صياغة الملاحظة بعبارات رصينة تليق بالتقارير الرسمية."
        ),
        REPHRASE_CREATIVE(
            "صياغة إبداعية حيوية",
            "أعد صياغة النص التالي بأسلوب حماسي، ممتع، تسويقي وإبداعي يجذب مشاعر واهتمام القراء:",
            "يزين النص بعبارات جذابة ونابضة بالحياة للعروض أو النشر."
        ),
        CODE_ASSISTANT(
            "مساعد المبرمج الذكي",
            "قم بتحليل النص أو الكود المرفق، حدد الثغرات والأخطاء إن وجدت، واشرح الحل ووفر نسخة كود محسنة وموثقة بالرموز والملاحظات:",
            "يحلل القطع البرمجية، يشرح الأخطاء ويوفر ترميزاً مثالياً مراجعاً."
        ),
        AUTO_CATEGORIZE(
            "تحليل واقتراح التصنيف",
            "حلل النص التالي واقترح اسماً لتصنيف أو مجلد مناسب يحتوي هذه الملاحظة (اختر كلمة واحدة معبرة فقط مثل: عمل، دراسة، مالي، أفكار، شخصي):",
            "يفهم نمط الموضوع ويقترح أفضل مجلد ملائم للملاحظة تلقائياً."
        ),
        EXAM_MAKER(
            "توليد اختبار ومراجعة",
            "أنشئ اختباراً تفاعلياً ومراجعة دورية (3 أسئلة مع إجاباتها النموذجية) بناءً على ما ورد من تفاصيل في النص التالي:",
            "يصنع أسئلة مراجعة تلخص أهم الجزئيات لتمكينك من قياس استيعابك للمعلومات."
        ),
        SENTIMENT(
            "تحليل نبرة المشاعر",
            "قم بتحليل نبرة النصوص والمشاعر العامة المعبر عنها من خلال الكلمات في هذا النص، وقدم خلاصة قصيرة واقتراحاً مناسباً:",
            "يكتشف مزاج ولغة النص ويقيس مستوى الإنتاجية أو المشاعر فيه."
        ),
        KEY_TAKEAWAYS(
            "استخلاص خلاصة النتائج",
            "ابحث عن النتائج الرئيسية، الأفكار الأساسية، والمفاهيم الكبرى التي يرتكز عليها النص وصغها باختصار رائع:",
            "يستنبط الجوهر المفيد للملاحظة ليوفر عليك قراءة كل التفاصيل مستقبلاً."
        ),
        MIND_MAP(
            "خريطة ذهنية ومخطط شجري",
            "صمم خريطة ذهنية ومخططاً شجرياً لتسلسل المواضيع والعلاقات المتبادلة بين النقاط المذكورة في النص بشكل مرئي مرتب ومثير:",
            "يرسم لك العلاقات والأقسام الفرعية بشكل يسهل حفظ الروابط العقلية للمحتوى."
        )
    }

    suspend fun runFeature(
        userCustomKey: String?,
        feature: AIFeature,
        content: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = if (!userCustomKey.isNullOrBlank()) {
            userCustomKey
        } else {
            BuildConfig.GEMINI_API_KEY
        }

        if (apiKey.isBlank()) {
            return@withContext "خطأ: لم يتم تمكين مفتاح API الخاص بـ Gemini! يرجى إضافته من خلال لوحة الأسرار البرمجية أو من الإعدادات لاستخدام ميزات الذكاء الاصطناعي."
        }

        val prompt = "${feature.promptPrefix}\n\n$content"
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "خطأ: لم نتمكن من الحصول على رد من الذكاء الاصطناعي. ربما المحتوى فارغ أو يحتوي على نصوص غير مدعومة."
        } catch (e: Exception) {
            "فشل استدعاء الذكاء الاصطناعي: ${e.localizedMessage ?: e.message}"
        }
    }

    suspend fun askCustomQuestion(
        userCustomKey: String?,
        contextContent: String,
        question: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = if (!userCustomKey.isNullOrBlank()) {
            userCustomKey
        } else {
            BuildConfig.GEMINI_API_KEY
        }

        if (apiKey.isBlank()) {
            return@withContext "خطأ: لم يتم تمكين مفتاح API الخاص بـ Gemini! يرجى إضافته من خلال لوحة الأسرار البرمجية أو من الإعدادات لاستخدام الدردشة."
        }

        val systemPrompt = "أنت مساعد ذكي ومحاور فطن مهمتك الإجابة عن أي سؤال يخص هذه المادة أو الملاحظة المرفقة تفصيلياً وبأعلى لباقة ووضوح باللغة العربية. المادة المرجعية المرفقة هي:\n\n$contextContent"
        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = "السؤال أو الاستفسار: $question")))
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "خطأ: لم تنجح عملية توليد الإجابة. أعد المحاولة."
        } catch (e: Exception) {
            "فشل التواصل مع المساعد الشات: ${e.localizedMessage ?: e.message}"
        }
    }
}
