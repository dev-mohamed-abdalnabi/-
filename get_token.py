"""
سكريبت مرة واحدة بس، تشغله على جهازك (مش على GitHub) عشان تولّد التوكن.
يطلع لك قيمة تحطها في GitHub Secret اسمه TOKEN_BASE64_HORROR.
"""
import base64
import json
from google_auth_oauthlib.flow import InstalledAppFlow

SCOPES = ["https://www.googleapis.com/auth/youtube.upload"]

flow = InstalledAppFlow.from_client_secrets_file("client_secret.json", SCOPES)
# هيفتح متصفح، سجّل دخول بحساب/قناة اليوتيوب الجديدة (الرعب) مش القديمة
creds = flow.run_local_server(port=0)

token_data = json.loads(creds.to_json())
token_b64 = base64.b64encode(json.dumps(token_data).encode("utf-8")).decode("utf-8")

print("\n=== انسخ القيمة دي وحطها في GitHub Secret باسم TOKEN_BASE64_HORROR ===\n")
print(token_b64)
