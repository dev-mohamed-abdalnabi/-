import os, requests, random, json, base64, sys, glob, asyncio, re
from datetime import datetime
import numpy as np
import moviepy.editor as mp
from moviepy.video.fx.all import loop
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

# ================== إعدادات الصوت والخطوط ==================
# أصوات Edge-TTS العربية المصرية (مجانية بالكامل)
VOICES = ['ar-EG-ShakirNeural', 'ar-EG-SalmaNeural']
TTS_VOICE = os.environ.get("TTS_VOICE", "ar-EG-ShakirNeural")

FONT_PATH_AR = "ArabicFont.ttf"
FONT_PATH_EN = "Roboto-Regular.ttf"

OPENROUTER_API_KEY = os.environ.get("OPENROUTER_API_KEY")
OPENROUTER_MODEL = os.environ.get("OPENROUTER_MODEL", "")

# قائمة موديلات مجانية احتياطية على OpenRouter، بيجرب واحد واحد لو الأول مش شغال
FALLBACK_MODELS = [
    "google/gemini-2.0-flash-exp:free",
    "meta-llama/llama-3.3-70b-instruct:free",
    "qwen/qwen-2.5-72b-instruct:free",
    "mistralai/mistral-7b-instruct:free",
]

def safe_wrap(text, width):
    words = text.split()
    lines = []
    current_line = []
    current_length = 0
    for word in words:
        if current_length + len(word) <= width:
            current_line.append(word)
            current_length += len(word) + 1
        else:
            if current_line: lines.append(" ".join(current_line))
            current_line = [word]
            current_length = len(word) + 1
    if current_line: lines.append(" ".join(current_line))
    return lines

def youtube_authenticate():
    TOKEN_B64 = os.environ.get("TOKEN_BASE64")
    token_data = json.loads(base64.b64decode(TOKEN_B64).decode('utf-8'))
    creds = Credentials.from_authorized_user_info(token_data)
    return build('youtube', 'v3', credentials=creds)

# ================== توليد قصة رعب أصلية ==================
def generate_scary_story():
    print("⏳ جاري توليد قصة رعب أصلية بالذكاء الاصطناعي...")

    if not OPENROUTER_API_KEY:
        raise Exception("OPENROUTER_API_KEY مش موجود في الـ environment variables / secrets!")

    system_prompt = (
        "أنت كاتب محترف لقصص الرعب القصيرة على طريقة حكايات Reddit (نقطة نظر أول شخص، "
        "واقعية، مشوّقة، نهاية صادمة أو مفتوحة). اكتب قصة رعب أصلية بالكامل (مش منقولة أو "
        "مقتبسة من أي مصدر موجود) باللغة العربية الفصحى المبسّطة، مناسبة لفيديو يوتيوب شورتس "
        "مدته حوالي 50 ثانية (حوالي 120-150 كلمة إجمالي). "
        "قسّم القصة إلى جمل قصيرة (كل جملة تقريباً 8-15 كلمة) تصلح لعرضها واحدة تلو الأخرى كترجمة على الشاشة. "
        "رد بصيغة JSON فقط بدون أي نص إضافي وبدون علامات markdown، بالشكل التالي:\n"
        '{"title": "عنوان جذاب قصير للقصة", "sentences": ["الجملة الأولى", "الجملة الثانية", "..."]}'
    )

    models_to_try = ([OPENROUTER_MODEL] if OPENROUTER_MODEL else []) + FALLBACK_MODELS

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
                            {"role": "system", "content": system_prompt},
                            {"role": "user", "content": "اكتب لي قصة رعب جديدة ومختلفة عن أي قصة سابقة."},
                        ],
                        "temperature": 1.0,
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
                if title and len(sentences) >= 3:
                    print(f"✅ نجح توليد القصة باستخدام الموديل: {model}")
                    return title, sentences
            except Exception as e:
                last_error = str(e)
                print(f"⚠️ محاولة {attempt+1} مع الموديل '{model}' فشلت: {e}")
                continue

    raise Exception(f"فشل توليد القصة بعد تجربة كل الموديلات! آخر خطأ: {last_error}")

# ================== تحويل الجمل لصوت (Edge TTS) ==================
async def _tts_save(text, out_path):
    communicate = edge_tts.Communicate(text, TTS_VOICE)
    await communicate.save(out_path)

def sentence_to_speech(text, out_path):
    asyncio.run(_tts_save(text, out_path))

def build_story_audio(sentences):
    audio_clips = []
    used_sentences = []
    current_duration = 0

    for i, sentence in enumerate(sentences):
        f_path = f"temp_{i}.mp3"
        sentence_to_speech(sentence, f_path)

        clip = mp.AudioFileClip(f_path)
        clip = clip.fx(mp.afx.audio_fadein, 0.02).fx(mp.afx.audio_fadeout, 0.02)

        if current_duration + clip.duration > MAX_DURATION and audio_clips:
            clip.close()
            os.remove(f_path)
            break

        audio_clips.append(clip)
        used_sentences.append(sentence)
        current_duration += clip.duration

    if not audio_clips:
        raise Exception("لم يتم توليد أي صوت للقصة!")

    return audio_clips, used_sentences, current_duration

def build_shorts_video():
    print("🚀 [1/4] تحضير القصة والصوت...")

    title, sentences = generate_scary_story()
    audio_clips, used_sentences, dur = build_story_audio(sentences)

    final_audio = mp.concatenate_audioclips(audio_clips)
    final_audio = final_audio.fx(mp.afx.audio_fadein, 0.5).fx(mp.afx.audio_fadeout, 1.0)

    starts = [0.0]
    for clip in audio_clips[:-1]:
        starts.append(starts[-1] + clip.duration)

    print("🎬 [2/4] اختيار خلفية رعب...")
    PEXELS_API_KEY = os.environ.get("PEXELS_API_KEY")
    headers = {'Authorization': PEXELS_API_KEY}

    horror_queries = [
        'dark forest night fog', 'abandoned house night', 'empty hallway dark',
        'foggy road night driving', 'old creepy building', 'dark basement',
        'night rain window', 'creepy woods flashlight'
    ]
    query = random.choice(horror_queries)

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

    dark = mp.ColorClip(size=(WIDTH, HEIGHT), color=(0, 0, 0), duration=dur).set_opacity(0.5)

    font_s = ImageFont.truetype(FONT_PATH_AR, 90)

    text_clips = []
    for i in range(len(audio_clips)):
        c_start = starts[i]
        c_end = starts[i+1] if i < len(starts)-1 else dur

        img = Image.new('RGBA', (WIDTH, HEIGHT), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)

        sentence = used_sentences[i]
        char_count = len(sentence)
        if char_count < 60:
            f_size, w_wrap, y_space = 95, 35, 120
        elif char_count < 120:
            f_size, w_wrap, y_space = 80, 40, 100
        else:
            f_size, w_wrap, y_space = 65, 45, 85

        font_ar_dynamic = ImageFont.truetype(FONT_PATH_AR, f_size)
        lines = safe_wrap(sentence, width=w_wrap)

        total_h = len(lines) * y_space
        y_off = max(HEIGHT * 0.55, (HEIGHT - total_h) / 2)

        for line in lines:
            d.text((WIDTH/2, y_off), line, font=font_ar_dynamic, fill="#F5F5F5", anchor="mm", stroke_width=5, stroke_fill="black", direction="rtl", language="ar")
            y_off += y_space

        t_clip = mp.ImageClip(np.array(img)).set_start(c_start).set_end(c_end)
        text_clips.append(t_clip)

    title_img = Image.new('RGBA', (WIDTH, HEIGHT), (0, 0, 0, 0))
    d_title = ImageDraw.Draw(title_img)
    d_title.multiline_text((WIDTH/2, 200), title, font=font_s, fill="#B00000", anchor="mm", align="center", spacing=25, stroke_width=5, stroke_fill="black", direction="rtl", language="ar")

    title_clip = mp.ImageClip(np.array(title_img)).set_duration(min(3.0, dur))
    final = mp.CompositeVideoClip([bg, dark, title_clip] + text_clips).set_audio(final_audio)

    print("⏳ [4/4] رندر سريع (1080p)...")
    final.write_videofile("final.mp4", fps=24, codec="libx264", audio_codec="aac", bitrate="8000k", preset="ultrafast", logger=None, threads=4)

    for f in glob.glob("temp_*.mp3"):
        os.remove(f)
    if os.path.exists("bg_v.mp4"):
        os.remove("bg_v.mp4")

    print("📡 الرفع لليوتيوب...")
    youtube = youtube_authenticate()

    title_templates = [
        "{title} 😱 #shorts #horror #قصص_رعب",
        "قصة رعب حقيقية.. {title} 👻 #shorts",
        "{title} | هل تجرؤ على السماع لآخرها؟ 🔦 #رعب",
        "لا تسمعها وأنت لوحدك.. {title} 😨 #shorts #horror",
        "{title} 🕯️ قصة مرعبة بصوت واقعي #قصص_مرعبة",
    ]

    desc_templates = [
        "{title}\n\nقصة رعب قصيرة مولّدة بالذكاء الاصطناعي لأغراض الترفيه فقط، أي تشابه مع أحداث حقيقية هو محض صدفة.\n\n#رعب #قصص_رعب #horror #shorts",
        "{title}\n\nهل تعرضت لموقف مشابه؟ شاركنا في التعليقات.\n\n#horror_shorts #قصص_مرعبة #رعب",
    ]

    v_title = random.choice(title_templates).format(title=title)
    v_desc = random.choice(desc_templates).format(title=title)

    body = {'snippet': {'title': v_title, 'description': v_desc, 'categoryId': '24'}, 'status': {'privacyStatus': 'public'}}
    youtube.videos().insert(part="snippet,status", body=body, media_body=MediaFileUpload("final.mp4", chunksize=-1, resumable=True)).execute()
    print(f"✅ تم بنجاح! (المدة: {dur:.1f} ثانية)")

if __name__ == "__main__":
    if not is_uploaded_today() or os.environ.get('GITHUB_EVENT_NAME') == 'workflow_dispatch':
        try:
            build_shorts_video()
            mark_uploaded_today()
        except Exception as e:
            print("🔥 خطأ:", e); sys.exit(1)
