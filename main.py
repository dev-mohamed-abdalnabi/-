import os, requests, random, json, base64, sys, glob, asyncio, re, subprocess
from datetime import datetime
import numpy as np
import moviepy.editor as mp
from moviepy.video.fx.all import loop, fadein, fadeout
from PIL import Image, ImageFont, ImageDraw
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload
import edge_tts

# ================== إعدادات الأبعاد ==================
WIDTH = 1080
HEIGHT = 1920
MAX_DURATION = 58.0

LOG_FILE = "daily_log.txt"

def today_str():
    return datetime.utcnow().strftime("%Y-%m-%d")

def is_uploaded_today():
    if not os.path.exists(LOG_FILE):
        return False
    with open(LOG_FILE, "r", encoding="utf-8") as f:
        return f.read().strip() == today_str()

def mark_uploaded_today():
    with open(LOG_FILE, "w", encoding="utf-8") as f:
        f.write(today_str())

# ================== الخطوط ==================
FONT_PATH_AR = "ArabicFont.ttf"
FONT_PATH_EN = "Roboto-Regular.ttf"

OPENROUTER_API_KEY = os.environ.get("OPENROUTER_API_KEY")
OPENROUTER_MODEL = os.environ.get("OPENROUTER_MODEL", "")

# ================== صوت أفضل (اختياري) ==================
# edge-tts مجاني لكنه محدود في التعبير والطبيعية. لو عايز صوت أحسن بكتير وأكثر
# طبيعية وتعبير (نبرة حقيقية بدل الإلقاء الجامد)، اعمل حساب على elevenlabs.io
# ودوّر في مكتبة الأصوات على صوت عربي (فيه أصوات عربي كتير كويسة جدًا)، وحط:
#   ELEVENLABS_API_KEY  = مفتاح الحساب
#   ELEVENLABS_VOICE_ID = الـ ID بتاع الصوت اللي اخترته
# لو الاتنين متسجلين، الكود هيستخدم ElevenLabs تلقائيًا بدل edge-tts.
# مفيش باقة مجانية لا نهائية في ElevenLabs (بتدفع بعد الكوتة المجانية الشهرية)،
# لكن جودة الصوت والتعبير فرق كبير عن edge-tts.
ELEVENLABS_API_KEY = os.environ.get("ELEVENLABS_API_KEY", "").strip()
ELEVENLABS_VOICE_ID = os.environ.get("ELEVENLABS_VOICE_ID", "").strip()
ELEVENLABS_MODEL = os.environ.get("ELEVENLABS_MODEL", "eleven_multilingual_v2")

# ================== بديل مجاني بالكامل وبدون أي حساب: Piper ==================
# Piper محرك تحويل نص-لصوت مفتوح المصدر (MIT) شغّال محلي بدون إنترنت وقت التشغيل
# وبدون أي تسجيل أو مفتاح API. مش هيبقى بالضرورة أحسن من edge-tts (اللي هو أصلاً
# نفس محرك Azure العملاق مجاني)، لكنه محرك مختلف تمامًا يستاهل تجربه لو عايز تقارن.
# مفعّل بس لو USE_PIPER=true، ولو فشل لأي سبب (تحميل الموديل، تثبيت الحزمة...)
# الكود بيرجع تلقائي لـ edge-tts عادي من غير ما يوقف الفيديو.
USE_PIPER = os.environ.get("USE_PIPER", "false").strip().lower() in ("1", "true", "yes")
PIPER_VOICE_NAME = os.environ.get("PIPER_VOICE_NAME", "ar_JO-kareem-medium")
PIPER_DIR = "piper_models"
PIPER_VOICE_URLS = {
    "ar_JO-kareem-medium": (
        "https://huggingface.co/rhasspy/piper-voices/resolve/main/ar/ar_JO/kareem/medium/ar_JO-kareem-medium.onnx",
        "https://huggingface.co/rhasspy/piper-voices/resolve/main/ar/ar_JO/kareem/medium/ar_JO-kareem-medium.onnx.json",
    ),
}

STATIC_FALLBACK_MODELS = [
    "meta-llama/llama-3.3-70b-instruct:free",
    "qwen/qwen3-coder:free",
    "google/gemma-3-27b-it:free",
    "openai/gpt-oss-120b:free",
]

# ================================================================
# أنواع المحتوى - بدل ما كانت رعب بس، دلوقتي فيه 3 أنواع بيتم
# اختيار واحد منهم عشوائي كل يوم عشان القناة متبقاش رتيبة
# ================================================================
CONTENT_TYPES = {
    "horror": {
        "weight": 45,
        "voice": "ar-EG-ShakirNeural",
        "rate": "+0%",
        "pitch": "+0Hz",
        "title_color": "#C41111",
        "badge_label": "🕯️ قصة رعب",
        "bg_queries": [
            'dark forest night fog', 'abandoned house night', 'empty hallway dark',
            'foggy road night driving', 'old creepy building', 'dark basement',
            'night rain window', 'creepy woods flashlight', 'abandoned hospital corridor',
        ],
        "system_prompt": (
            "أنت كاتب سيناريو محترف متخصص في قصص الرعب القصيرة لمنصات مثل يوتيوب شورتس وتيك توك، "
            "أسلوبك أقرب لحكايات Reddit الواقعية (نقطة نظر أول شخص). اكتب قصة رعب أصلية بالكامل "
            "(غير منسوخة أو مقتبسة من أي مصدر) بالعربية الفصحى المبسّطة السهلة النطق، لفيديو مدته "
            "حوالي 45-55 ثانية (130-160 كلمة إجمالي).\n\n"
            "التزم بالتالي بدقة:\n"
            "1. الجملة الأولى لازم تكون خطّاف قوي يدخل المستمع في التوتر فورًا، من غير مقدمات عامة.\n"
            "2. اذكر تفاصيل حسية ملموسة (صوت، رائحة، مكان محدد، توقيت) بدل الكلام العام المطاطي.\n"
            "3. صعّد التوتر تدريجيًا جملة بعد جملة، وخليه يتصاعد فعلاً مش يفضل ثابت.\n"
            "4. النهاية لازم تكون صادمة أو مفتوحة تسيب المشاهد قلقان، مش نهاية مطمئنة ولا مبتذلة.\n"
            "5. ممنوع تمامًا تبدأ بأي من الجمل دي أو أي جملة شبيهة بيها: "
            "\"كان يا ما كان\"، \"في ليلة من الليالي\"، \"لم أستطع النوم\"، \"منذ صغري وأنا\"، "
            "\"هذه قصة حقيقية حدثت معي\"، \"كل شيء بدأ عندما\".\n"
            "6. غيّر المكان والشخصيات والموقف كل مرة (بيت، مصعد، طريق، مستشفى، مدرسة، قطار، شقة جديدة... "
            "نوّع ولا تكرر نفس الفكرة من قصة سابقة).\n"
            "7. ممنوع كتابة أي أرقام بصيغة رقمية (مثل 3 أو 2023)، اكتبها بالحروف دايمًا لأن الصوت الصناعي "
            "بينطق الأرقام غلط. ممنوع أيضًا أي كلمات إنجليزية أو اختصارات لاتينية.\n"
            "8. تجنب العنف الصريح والمقزز، ركّز على الرعب النفسي والترقب لا على التفاصيل الدموية.\n"
            "9. قسّم القصة لجمل قصيرة (8-15 كلمة تقريبًا) تصلح كل واحدة سطر ترجمة منفصل على الشاشة، "
            "لكن لازم كل جملة تكون امتداد منطقي مباشر للي قبلها (نفس الشخصية، نفس المكان، نفس الخيط "
            "الزمني)، بحيث لو حد قرأ الجمل ورا بعض حسّها فقرة واحدة متصلة، مش جمل مبعثرة كل واحدة لوحدها.\n\n"
            "رد بصيغة JSON فقط بدون أي نص إضافي وبدون علامات markdown:\n"
            '{"title": "عنوان جذاب قصير (3-6 كلمات)", "sentences": ["الجملة الأولى", "الجملة الثانية", "..."]}'
        ),
        "title_templates": [
            "{title} 😱 #shorts #horror #قصص_رعب",
            "قصة رعب حقيقية.. {title} 👻 #shorts",
            "{title} | هل تجرؤ على السماع لآخرها؟ 🔦 #رعب",
            "لا تسمعها وأنت لوحدك.. {title} 😨 #shorts #horror",
        ],
        "desc_templates": [
            "{title}\n\nقصة رعب قصيرة مولّدة بالذكاء الاصطناعي لأغراض الترفيه فقط، أي تشابه مع أحداث حقيقية هو محض صدفة.\n\n#رعب #قصص_رعب #horror #shorts",
            "{title}\n\nهل تعرضت لموقف مشابه؟ شاركنا في التعليقات.\n\n#horror_shorts #قصص_مرعبة #رعب",
        ],
    },
    "facts": {
        "weight": 30,
        "voice": "ar-EG-SalmaNeural",
        "rate": "+0%",
        "pitch": "+0Hz",
        "title_color": "#0E8A6C",
        "badge_label": "💡 معلومات غريبة",
        "bg_queries": [
            'galaxy stars space timelapse', 'deep ocean blue dark', 'brain neurons abstract',
            'ancient ruins mysterious', 'northern lights night sky', 'desert dunes aerial night',
            'old library books close up', 'clock gears macro',
        ],
        "system_prompt": (
            "أنت كاتب محتوى متخصص في المعلومات الغريبة والصادمة (نوع محتوى 'هل تعلم' الفيروسي) "
            "لفيديوهات يوتيوب شورتس. اكتب سلسلة من خمسة إلى سبعة معلومات حقيقية وغريبة ومثيرة للدهشة "
            "عن موضوع واحد مشترك (اختر أنت الموضوع: الفضاء، جسم الإنسان، المحيطات، التاريخ، الحيوانات، "
            "علم النفس، ظواهر كونية، حضارات قديمة...)، بالعربية الفصحى المبسّطة.\n\n"
            "التزم بالتالي:\n"
            "1. المعلومة الأولى لازم تكون صادمة جدًا عشان تشد الانتباه من أول ثانيتين.\n"
            "2. كل معلومة لازم تكون حقيقية وقابلة للتحقق، مش اختراع أو مبالغة كاذبة.\n"
            "3. رتّب المعلومات بحيث تتصاعد في الغرابة، وخلي آخر معلومة هي الأكثر جنونًا (تصلح كخاتمة قوية).\n"
            "4. كل معلومة جملة أو جملتين قصار (10-18 كلمة) تصلح سطر ترجمة على الشاشة.\n"
            "5. ممنوع كتابة أرقام بصيغة رقمية (اكتبها بالحروف)، وممنوع كلمات إنجليزية أو اختصارات لاتينية.\n"
            "6. اربط المعلومات ببعض بانتقال طبيعي بسيط بدل ما تبقى جمل مفككة، بحيث المعلومة الجديدة "
            "تكمل خيط المعلومة اللي قبلها (نفس الموضوع بالظبط، تصاعد منطقي)، مش قفزات عشوائية بين أفكار.\n\n"
            "رد بصيغة JSON فقط بدون أي نص إضافي وبدون علامات markdown:\n"
            '{"title": "عنوان جذاب قصير (3-6 كلمات) عن الموضوع المختار", "sentences": ["المعلومة الأولى", "المعلومة الثانية", "..."]}'
        ),
        "title_templates": [
            "هل تعلم أن {title}؟! 🤯 #shorts #معلومات_غريبة",
            "{title}.. حقائق هتصدمك 🧠 #shorts #حقائق",
            "معلومات غريبة عن {title} 😳 #shorts",
        ],
        "desc_templates": [
            "{title}\n\nمعلومات حقيقية وغريبة لأغراض تعليمية وترفيهية.\n\n#معلومات_غريبة #حقائق #shorts #هل_تعلم",
        ],
    },
    "mystery": {
        "weight": 25,
        "voice": "ar-EG-ShakirNeural",
        "rate": "+0%",
        "pitch": "+0Hz",
        "title_color": "#B8860B",
        "badge_label": "🌫️ لغز غامض",
        "bg_queries": [
            'foggy forest night mysterious', 'old abandoned ship', 'vintage newspaper archive',
            'empty street night fog', 'old photograph vintage', 'dark ocean waves night',
            'creepy attic old', 'mysterious cave dark',
        ],
        "system_prompt": (
            "أنت كاتب محتوى متخصص في الألغاز والقصص الغامضة غير المحلولة (حقيقية أو قريبة من الواقع) "
            "لفيديوهات يوتيوب شورتس، أسلوب تشويقي درامي. اكتب عن لغز أو حادثة غامضة (اختفاء، حادثة "
            "غير مفسّرة، ظاهرة غريبة موثّقة) بالعربية الفصحى المبسّطة، حوالي 130-160 كلمة.\n\n"
            "التزم بالتالي:\n"
            "1. ابدأ بالسؤال أو الموقف الغامض مباشرة من غير مقدمات.\n"
            "2. اسرد الوقائع بشكل تصاعدي مشوّق، مع ذكر تفاصيل مكان وزمان لو معروفة.\n"
            "3. اختم بالسؤال أو الاحتمالية المفتوحة اللي خلّت اللغز يفضل بدون حل لحد دلوقتي، "
            "من غير ما تدّعي معلومة غير مؤكدة كأنها حقيقة قطعية.\n"
            "4. ممنوع كتابة أرقام بصيغة رقمية (اكتبها بالحروف)، وممنوع كلمات إنجليزية.\n"
            "5. قسّم القصة لجمل قصيرة (8-15 كلمة) تصلح سطر ترجمة على الشاشة، وخلي كل جملة "
            "استكمال مباشر ومترابط منطقيًا مع اللي قبلها زي حكاية واحدة متصلة مش نقط متفرقة.\n\n"
            "رد بصيغة JSON فقط بدون أي نص إضافي وبدون علامات markdown:\n"
            '{"title": "عنوان جذاب قصير (3-6 كلمات)", "sentences": ["الجملة الأولى", "الجملة الثانية", "..."]}'
        ),
        "title_templates": [
            "لغز {title} لسه محدش حله 🕵️ #shorts #لغز",
            "{title}.. القصة الغامضة اللي هتفضل تفكر فيها 🌫️ #shorts",
            "ما الذي حدث في {title}؟ 👁️ #shorts #غموض",
        ],
        "desc_templates": [
            "{title}\n\nسرد درامي لحادثة/لغز غامض بغرض الترفيه، بعض التفاصيل قد تكون مبسّطة أو غير مؤكدة بالكامل.\n\n#لغز #غموض #شورتس",
        ],
    },
}

# لو عايز تجرب صوت تاني بسرعة من غير ما تعدل كل نوع محتوى لوحده، حدد
# TTS_VOICE_OVERRIDE في الـ secrets/env وهيتطبق على كل الأنواع.
# أصوات عربية تستاهل تجربة: ar-EG-ShakirNeural / ar-EG-SalmaNeural (مصري)
# ar-SA-HamedNeural / ar-SA-ZariyahNeural (سعودي، بينطق الفصحى أوضح غالبًا)
TTS_VOICE_OVERRIDE = os.environ.get("TTS_VOICE_OVERRIDE", "").strip()
if TTS_VOICE_OVERRIDE:
    for _cfg in CONTENT_TYPES.values():
        _cfg["voice"] = TTS_VOICE_OVERRIDE

BANNED_OPENERS = [
    "كان يا ما كان", "في ليلة من الليالي", "لم أستطع النوم", "منذ صغري",
    "هذه قصة حقيقية حدثت معي", "كل شيء بدأ عندما", "في إحدى الليالي",
]

def pick_content_type():
    types = list(CONTENT_TYPES.keys())
    weights = [CONTENT_TYPES[t]["weight"] for t in types]
    return random.choices(types, weights=weights, k=1)[0]

def get_free_models():
    """يسأل OpenRouter عن قائمة الموديلات المجانية المتاحة فعلياً دلوقتي، ويرتبها من الأقوى للأضعف."""
    TRUSTED_PROVIDERS = [
        "google/", "deepseek/", "qwen/", "meta-llama/",
        "mistralai/", "nvidia/", "openai/", "moonshotai/", "z-ai/",
    ]
    EXCLUDE_KEYWORDS = ["vision", "embed", "guard", "moderation", "-base", "coder"]

    try:
        resp = requests.get(
            "https://openrouter.ai/api/v1/models",
            headers={"Authorization": f"Bearer {OPENROUTER_API_KEY}"} if OPENROUTER_API_KEY else {},
            timeout=30,
        )
        data = resp.json().get("data", [])
        candidates = []
        for m in data:
            pricing = m.get("pricing", {})
            model_id = m.get("id", "")
            context_len = m.get("context_length", 0) or 0
            try:
                prompt_price = float(pricing.get("prompt", "1"))
                completion_price = float(pricing.get("completion", "1"))
            except (TypeError, ValueError):
                continue

            if not (prompt_price == 0 and completion_price == 0 and model_id.endswith(":free")):
                continue
            if any(bad in model_id.lower() for bad in EXCLUDE_KEYWORDS):
                continue

            is_trusted = any(model_id.startswith(p) for p in TRUSTED_PROVIDERS)
            candidates.append((is_trusted, context_len, model_id))

        candidates.sort(key=lambda x: (not x[0], -x[1]))
        return [c[2] for c in candidates]
    except Exception as e:
        print(f"⚠️ فشل جلب قائمة الموديلات المجانية: {e}")
        return []

def wrap_by_pixels(text, font, draw, max_width):
    """تقسيم صحيح بالبكسل الفعلي (مش بعدد الحروف) عشان أي سطر متعديش عرض الشاشة
    ويتقطع/يتاكل من الحواف."""
    words = text.split()
    lines = []
    current = []
    for word in words:
        trial = " ".join(current + [word])
        w = draw.textlength(trial, font=font)
        if w <= max_width or not current:
            current.append(word)
        else:
            lines.append(" ".join(current))
            current = [word]
    if current:
        lines.append(" ".join(current))
    return lines

def youtube_authenticate():
    TOKEN_B64 = os.environ.get("TOKEN_BASE64")
    token_data = json.loads(base64.b64decode(TOKEN_B64).decode('utf-8'))
    creds = Credentials.from_authorized_user_info(token_data)
    return build('youtube', 'v3', credentials=creds)

# ================== تنظيف النص قبل تحويله لصوت ==================
def preprocess_for_tts(text):
    """شبكة أمان: يشيل أي حروف/رموز ممكن تخلي الصوت الصناعي ينطق غلط أو يتوقف فجأة."""
    text = re.sub(r"[A-Za-z]+", "", text)          # يشيل أي كلمات إنجليزية لو فلتت من الموديل
    text = re.sub(r"[#*_~`\[\]{}<>]", "", text)     # رموز markdown/برمجية غريبة
    text = re.sub(r"\s{2,}", " ", text).strip()
    return text

# ================== توليد المحتوى (قصة/معلومات/لغز) ==================
def generate_content(content_type):
    cfg = CONTENT_TYPES[content_type]
    print(f"⏳ جاري توليد محتوى نوع '{content_type}' بالذكاء الاصطناعي...")

    if not OPENROUTER_API_KEY:
        raise Exception("OPENROUTER_API_KEY مش موجود في الـ environment variables / secrets!")

    dynamic_models = get_free_models()
    models_to_try = (
        ([OPENROUTER_MODEL] if OPENROUTER_MODEL else [])
        + dynamic_models[:8]
        + STATIC_FALLBACK_MODELS
    )
    seen = set()
    models_to_try = [m for m in models_to_try if m and not (m in seen or seen.add(m))]

    print(f"📋 هيتم تجربة {len(models_to_try)} موديل: {models_to_try}")

    last_error = None
    for model in models_to_try:
        for attempt in range(2):
            try:
                resp = requests.post(
                    "https://openrouter.ai/api/v1/chat/completions",
                    headers={
                        "Authorization": f"Bearer {OPENROUTER_API_KEY}",
                        "Content-Type": "application/json",
                    },
                    json={
                        "model": model,
                        "messages": [
                            {"role": "system", "content": cfg["system_prompt"]},
                            {"role": "user", "content": "اكتب محتوى جديد ومختلف تمامًا عن أي محتوى سابق."},
                        ],
                        "temperature": 1.05,
                    },
                    timeout=60,
                )
                data = resp.json()

                if "choices" not in data:
                    print(f"⚠️ الموديل '{model}' رجّع رد بدون choices. الرد الكامل:")
                    print(json.dumps(data, ensure_ascii=False, indent=2))
                    last_error = data.get("error", {}).get("message", "unknown error")
                    break

                raw = data["choices"][0]["message"]["content"].strip()
                raw = re.sub(r"^```(json)?|```$", "", raw.strip(), flags=re.MULTILINE).strip()
                parsed = json.loads(raw)
                title = parsed["title"].strip()
                sentences = [s.strip() for s in parsed["sentences"] if s.strip()]

                if not title or len(sentences) < 3:
                    continue

                # فحص بسيط: ارفض لو بدأت بجملة مستهلكة/كليشيه
                first = sentences[0]
                if any(first.startswith(b) for b in BANNED_OPENERS):
                    print("⚠️ افتتاحية مكرورة/كليشيه، بيتم إعادة المحاولة...")
                    continue

                print(f"✅ نجح توليد المحتوى باستخدام الموديل: {model}")
                return title, sentences
            except Exception as e:
                last_error = str(e)
                print(f"⚠️ محاولة {attempt+1} مع الموديل '{model}' فشلت: {e}")
                continue

    raise Exception(f"فشل توليد المحتوى بعد تجربة كل الموديلات! آخر خطأ: {last_error}")

# ================== تحويل الجمل لصوت (Edge TTS) ==================
async def _tts_save(text, out_path, voice, rate, pitch):
    communicate = edge_tts.Communicate(text, voice, rate=rate, pitch=pitch)
    await communicate.save(out_path)

def _tts_elevenlabs(text, out_path):
    url = f"https://api.elevenlabs.io/v1/text-to-speech/{ELEVENLABS_VOICE_ID}"
    headers = {
        "xi-api-key": ELEVENLABS_API_KEY,
        "Content-Type": "application/json",
        "Accept": "audio/mpeg",
    }
    payload = {
        "text": text,
        "model_id": ELEVENLABS_MODEL,
        "voice_settings": {
            "stability": 0.45,
            "similarity_boost": 0.85,
            "style": 0.35,
            "use_speaker_boost": True,
        },
    }
    resp = requests.post(url, headers=headers, json=payload, timeout=90)
    resp.raise_for_status()
    with open(out_path, "wb") as f:
        f.write(resp.content)

def _ensure_piper_model():
    os.makedirs(PIPER_DIR, exist_ok=True)
    onnx_path = os.path.join(PIPER_DIR, f"{PIPER_VOICE_NAME}.onnx")
    json_path = onnx_path + ".json"
    if os.path.exists(onnx_path) and os.path.exists(json_path):
        return onnx_path, json_path
    urls = PIPER_VOICE_URLS.get(PIPER_VOICE_NAME)
    if not urls:
        raise Exception(f"مفيش رابط تحميل معروف لصوت Piper اسمه: {PIPER_VOICE_NAME}")
    onnx_url, json_url = urls
    r1 = requests.get(onnx_url, timeout=120); r1.raise_for_status()
    with open(onnx_path, "wb") as f: f.write(r1.content)
    r2 = requests.get(json_url, timeout=60); r2.raise_for_status()
    with open(json_path, "wb") as f: f.write(r2.content)
    return onnx_path, json_path

def _tts_piper(text, out_base):
    onnx_path, json_path = _ensure_piper_model()
    wav_path = out_base + ".wav"
    proc = subprocess.run(
        ["piper", "--model", onnx_path, "--config", json_path, "--output_file", wav_path],
        input=text.encode("utf-8"), capture_output=True, timeout=60,
    )
    if proc.returncode != 0 or not os.path.exists(wav_path):
        raise Exception(f"piper CLI فشل: {proc.stderr.decode(errors='ignore')[:300]}")
    return wav_path

def sentence_to_speech(text, out_base, voice, rate, pitch):
    """بيرجع مسار الملف الصوتي الفعلي اللي اتعمل (الامتداد بيتغيّر حسب المحرك المستخدم)."""
    if ELEVENLABS_API_KEY and ELEVENLABS_VOICE_ID:
        try:
            path = out_base + ".mp3"
            _tts_elevenlabs(text, path)
            return path
        except Exception as e:
            print(f"⚠️ فشل نداء ElevenLabs، هيتم تجربة البديل التالي: {e}")

    if USE_PIPER:
        try:
            return _tts_piper(text, out_base)
        except Exception as e:
            print(f"⚠️ فشل Piper، هيتم الرجوع لـ edge-tts: {e}")

    path = out_base + ".mp3"
    asyncio.run(_tts_save(text, path, voice, rate, pitch))
    return path

def make_silence(duration=0.22, fps=44100):
    return mp.AudioClip(lambda t: np.array([0, 0]), duration=duration, fps=fps)

def build_story_audio(sentences, voice, rate, pitch):
    audio_clips = []
    used_sentences = []
    current_duration = 0

    for i, sentence in enumerate(sentences):
        clean = preprocess_for_tts(sentence)
        if not clean:
            continue
        f_base = f"temp_{i}"
        f_path = sentence_to_speech(clean, f_base, voice, rate, pitch)

        clip = mp.AudioFileClip(f_path)
        clip = clip.fx(mp.afx.audio_fadein, 0.02).fx(mp.afx.audio_fadeout, 0.02)

        gap = make_silence(0.22) if audio_clips else None
        added_dur = clip.duration + (gap.duration if gap else 0)

        if current_duration + added_dur > MAX_DURATION and audio_clips:
            clip.close()
            os.remove(f_path)
            break

        if gap is not None:
            audio_clips.append(gap)
        audio_clips.append(clip)
        used_sentences.append(sentence)
        current_duration += added_dur

    if not audio_clips:
        raise Exception("لم يتم توليد أي صوت للمحتوى!")

    return audio_clips, used_sentences, current_duration

def compute_sentence_starts(audio_clips, n_sentences):
    """السكتات القصيرة بين الجمل بقت جزء من قايمة audio_clips، فبنحسب بداية كل جملة فعلية
    بمطابقة الكليبات اللي مدتها أطول من مدة السكتة الثابتة."""
    starts = []
    running = 0.0
    found = 0
    for clip in audio_clips:
        is_silence_gap = abs(clip.duration - 0.22) < 0.01
        if not is_silence_gap and found < n_sentences:
            starts.append(running)
            found += 1
        running += clip.duration
    return starts

# ================== أدوات بصرية ==================
def build_gradient_overlay(width, height, dur):
    """تدرج داكن سينمائي: غامق أعلى (خلف العنوان) وأسفل (خلف الترجمة)، وأفتح في النص."""
    grad = np.zeros((height, width, 4), dtype=np.uint8)
    for y in range(height):
        p = y / height
        if p < 0.20:
            alpha = 150 * (1 - p / 0.20) + 70
        elif p > 0.52:
            alpha = 70 + min(1.0, (p - 0.52) / 0.40) * 150
        else:
            alpha = 70
        grad[y, :, 3] = int(min(255, max(0, alpha)))
    return mp.ImageClip(grad).set_duration(dur)

def build_context_badge(label, width, height, dur, font_path):
    """شريط صغير ثابت طول الفيديو بيفضل يفكّر المشاهد بنوع المحتوى (رعب/معلومات/لغز)
    حتى لو دخل نص الفيديو وهو ماسكش أول ثانية."""
    img = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    font = ImageFont.truetype(font_path, 40)
    pad_x, pad_y = 26, 14
    tw = d.textlength(label, font=font)
    box_w, box_h = tw + pad_x * 2, 40 + pad_y * 2
    x0, y0 = 30, 55
    d.rounded_rectangle([x0, y0, x0 + box_w, y0 + box_h], radius=box_h / 2, fill=(0, 0, 0, 110))
    d.text((x0 + box_w / 2, y0 + box_h / 2), label, font=font, fill="#FFFFFF", anchor="mm",
           direction="rtl", language="ar")
    return mp.ImageClip(np.array(img)).set_duration(dur)

def ken_burns(clip, dur, zoom=0.06):
    """زووم بطيء وناعم على الخلفية عشان الفيديو يبقى حيوي مش ثابت جامد."""
    return clip.resize(lambda t: 1 + zoom * (t / dur)).set_position(("center", "center"))

def draw_caption_image(sentence, width, height, font_path):
    img = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    safe_max_width = width * 0.84  # هامش أمان يمين وشمال عشان النص ميلمسش حواف الشاشة
    max_lines_allowed = 4

    char_count = len(sentence)
    f_size = 92 if char_count < 60 else (76 if char_count < 120 else 62)
    f_size = min(f_size, 100)

    # نقلل حجم الخط تدريجيًا لحد ما كل الأسطر تدخل داخل عرض الشاشة الآمن
    # وعدد الأسطر يبقى معقول، عشان محدش سطر يتقطع من الحواف
    while f_size > 36:
        font = ImageFont.truetype(font_path, f_size)
        lines = wrap_by_pixels(sentence, font, d, safe_max_width)
        max_line_w = max(d.textlength(line, font=font) for line in lines)
        if max_line_w <= safe_max_width and len(lines) <= max_lines_allowed:
            break
        f_size -= 4
    else:
        font = ImageFont.truetype(font_path, f_size)
        lines = wrap_by_pixels(sentence, font, d, safe_max_width)

    line_bbox = font.getbbox("عجهقض")
    y_space = int((line_bbox[3] - line_bbox[1]) * 1.35)
    total_h = len(lines) * y_space

    y_off = max(height * 0.58, (height - total_h) / 2 + height * 0.16)

    max_line_w = max(d.textlength(line, font=font) for line in lines)
    box_pad_x, box_pad_y = 55, 30
    box_w = min(max_line_w + box_pad_x * 2, width - 30)
    box_h = total_h + box_pad_y * 2
    box_x0 = (width - box_w) / 2
    box_y0 = y_off - y_space * 0.65 - box_pad_y
    d.rounded_rectangle(
        [box_x0, box_y0, box_x0 + box_w, box_y0 + box_h],
        radius=34, fill=(10, 10, 12, 150),
    )

    for line in lines:
        d.text((width / 2, y_off), line, font=font, fill="#F7F7F7", anchor="mm",
                stroke_width=4, stroke_fill="black", direction="rtl", language="ar")
        y_off += y_space

    return np.array(img)

def build_shorts_video():
    print("🚀 [1/4] تحضير المحتوى والصوت...")

    content_type = pick_content_type()
    cfg = CONTENT_TYPES[content_type]
    print(f"🎯 نوع المحتوى المختار اليوم: {content_type}")

    title, sentences = generate_content(content_type)
    audio_clips, used_sentences, dur = build_story_audio(sentences, cfg["voice"], cfg["rate"], cfg["pitch"])

    final_audio = mp.concatenate_audioclips(audio_clips)
    final_audio = final_audio.fx(mp.afx.audio_fadein, 0.4).fx(mp.afx.audio_fadeout, 1.0)

    starts = compute_sentence_starts(audio_clips, len(used_sentences))

    print("🎬 [2/4] اختيار خلفية مناسبة...")
    PEXELS_API_KEY = os.environ.get("PEXELS_API_KEY")
    headers = {'Authorization': PEXELS_API_KEY}

    query = random.choice(cfg["bg_queries"])
    v_res = requests.get(f'https://api.pexels.com/videos/search?query={query}&orientation=portrait&per_page=30', headers=headers).json()
    videos = v_res.get('videos', [])
    valid_videos = [v for v in videos if v.get('duration', 0) >= dur]

    if valid_videos:
        selected_video = random.choice(valid_videos)
    elif videos:
        selected_video = max(videos, key=lambda x: x.get('duration', 0))
    else:
        raise Exception("لم يتم العثور على فيديوهات من Pexels!")

    v_url = selected_video['video_files'][0]['link']
    with open("bg_v.mp4", "wb") as f: f.write(requests.get(v_url).content)

    print(f"⚙️ [3/4] المونتاج...")
    bg = loop(mp.VideoFileClip("bg_v.mp4").resize(height=HEIGHT).crop(x1=0, y1=0, width=WIDTH, height=HEIGHT), duration=dur)
    bg = bg.subclip(0, dur)
    bg = ken_burns(bg, dur)

    gradient = build_gradient_overlay(WIDTH, HEIGHT, dur)
    badge = build_context_badge(cfg["badge_label"], WIDTH, HEIGHT, dur, FONT_PATH_AR)

    font_title = ImageFont.truetype(FONT_PATH_AR, 88)

    text_clips = []
    for i in range(len(starts)):
        c_start = starts[i]
        c_end = starts[i + 1] if i < len(starts) - 1 else dur

        img_arr = draw_caption_image(used_sentences[i], WIDTH, HEIGHT, FONT_PATH_AR)
        seg_dur = max(0.05, c_end - c_start)
        fade_d = min(0.18, seg_dur * 0.3)

        t_clip = (mp.ImageClip(img_arr)
                  .set_start(c_start).set_end(c_end)
                  .fx(fadein, fade_d).fx(fadeout, fade_d))
        text_clips.append(t_clip)

    title_img = Image.new('RGBA', (WIDTH, HEIGHT), (0, 0, 0, 0))
    d_title = ImageDraw.Draw(title_img)
    d_title.multiline_text((WIDTH / 2, 190), title, font=font_title, fill=cfg["title_color"],
                            anchor="mm", align="center", spacing=25, stroke_width=6,
                            stroke_fill="black", direction="rtl", language="ar")

    title_dur = min(3.2, dur)
    title_clip = (mp.ImageClip(np.array(title_img))
                  .set_duration(title_dur)
                  .fx(fadein, 0.35).fx(fadeout, 0.4))

    final = mp.CompositeVideoClip([bg, gradient, badge, title_clip] + text_clips, size=(WIDTH, HEIGHT)).set_audio(final_audio)

    print("⏳ [4/4] رندر (1080p)...")
    final.write_videofile("final.mp4", fps=24, codec="libx264", audio_codec="aac", bitrate="8000k", preset="ultrafast", logger=None, threads=4)

    for f in glob.glob("temp_*.mp3") + glob.glob("temp_*.wav"):
        os.remove(f)
    if os.path.exists("bg_v.mp4"):
        os.remove("bg_v.mp4")

    print("📡 الرفع لليوتيوب...")
    youtube = youtube_authenticate()

    v_title = random.choice(cfg["title_templates"]).format(title=title)
    v_desc = random.choice(cfg["desc_templates"]).format(title=title)

    body = {'snippet': {'title': v_title, 'description': v_desc, 'categoryId': '24'}, 'status': {'privacyStatus': 'public'}}
    youtube.videos().insert(part="snippet,status", body=body, media_body=MediaFileUpload("final.mp4", chunksize=-1, resumable=True)).execute()
    print(f"✅ تم بنجاح! (النوع: {content_type} | المدة: {dur:.1f} ثانية)")

if __name__ == "__main__":
    if not is_uploaded_today() or os.environ.get('GITHUB_EVENT_NAME') == 'workflow_dispatch':
        try:
            build_shorts_video()
            mark_uploaded_today()
        except Exception as e:
            print("🔥 خطأ:", e); sys.exit(1)
