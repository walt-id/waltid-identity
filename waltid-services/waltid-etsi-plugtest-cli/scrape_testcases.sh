#!/usr/bin/env bash
# scrape_testcases.sh - Fetch the latest test case definitions from the ETSI plugtest portal
# and rebuild test-cases.json from the scraped files.
#
# Usage:
#   ./scrape_testcases.sh
#
# Requires: curl, python3, jq
# Credentials are read from login.sh (same directory as this script or parent eea-data/).

set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TESTCASES_DIR="$MODULE_DIR/testcases"
TEST_CASES_JSON="$MODULE_DIR/test-cases.json"

# login.sh can be passed as argument or auto-detected relative to MODULE_DIR
LOGIN_SCRIPT="${1:-}"
if [[ -z "$LOGIN_SCRIPT" ]]; then
    # Walk up from MODULE_DIR looking for eea-data/login.sh
    SEARCH="$MODULE_DIR"
    while [[ "$SEARCH" != "/" ]]; do
        if [[ -f "$SEARCH/eea-data/login.sh" ]]; then
            LOGIN_SCRIPT="$SEARCH/eea-data/login.sh"
            break
        fi
        SEARCH="$(dirname "$SEARCH")"
    done
fi

# ── Sanity checks ─────────────────────────────────────────────────────────────
[[ -f "$LOGIN_SCRIPT" ]] || { echo "ERROR: login.sh not found at $LOGIN_SCRIPT" >&2; exit 1; }
command -v python3 >/dev/null || { echo "ERROR: python3 not found" >&2; exit 1; }
command -v jq >/dev/null || { echo "ERROR: jq not found" >&2; exit 1; }

mkdir -p "$TESTCASES_DIR"

# ── Authenticate ──────────────────────────────────────────────────────────────
echo "Authenticating..."
TOKEN=$(bash "$LOGIN_SCRIPT" 2>/dev/null)
[[ -n "$TOKEN" ]] || { echo "ERROR: Failed to obtain access token" >&2; exit 1; }
echo "OK"

# ── Fetch JS bundle URL ───────────────────────────────────────────────────────
echo "Fetching portal homepage to find JS bundle..."
HOMEPAGE=$(curl -s 'https://signature-plugtests.etsi.org/' \
  -H 'Accept: text/html' \
  -H "Authorization: Bearer $TOKEN")
JS_FILE=$(echo "$HOMEPAGE" | grep -oE '/js/app\.[a-f0-9]+\.js' | head -1)
[[ -n "$JS_FILE" ]] || { echo "ERROR: Could not find JS bundle URL in homepage" >&2; exit 1; }
JS_URL="https://signature-plugtests.etsi.org$JS_FILE"
echo "JS bundle: $JS_URL"

# ── Download JS bundle ────────────────────────────────────────────────────────
echo "Downloading JS bundle..."
JS_TMP=$(mktemp /tmp/etsi-app-XXXXXX.js)
curl -s "$JS_URL" -o "$JS_TMP"
echo "Downloaded $(wc -c < "$JS_TMP") bytes"

# ── Extract test case data from JS bundle ─────────────────────────────────────
echo "Extracting test cases..."
python3 - "$JS_TMP" "$TESTCASES_DIR" << 'PYEOF'
import sys, re, json, os
from datetime import datetime, timezone

js_path      = sys.argv[1]
testcases_dir = sys.argv[2]

with open(js_path) as f:
    content = f.read()

def extract_testcases_array(content, start_pos):
    chunk = content[start_pos:start_pos+400000]
    array_start = chunk.find('[{sheetName:')
    if array_start == -1: return None
    depth = 0
    in_string = False
    string_char = None
    escape_next = False
    result_chars = []
    i = array_start
    while i < len(chunk):
        c = chunk[i]
        if escape_next:
            escape_next = False
            result_chars.append(c)
            i += 1
            continue
        if c == '\\' and in_string:
            escape_next = True
            result_chars.append(c)
            i += 1
            continue
        if not in_string and c in ('"', "'"):
            in_string = True
            string_char = c
            result_chars.append('"')
            i += 1
            continue
        elif in_string and c == string_char:
            in_string = False
            string_char = None
            result_chars.append('"')
            i += 1
            continue
        elif in_string and c == '"' and string_char == "'":
            result_chars.append('\\"')
            i += 1
            continue
        elif not in_string:
            if c == '[': depth += 1
            elif c == ']':
                depth -= 1
                if depth == 0:
                    result_chars.append(c)
                    break
        result_chars.append(c)
        i += 1
    return ''.join(result_chars)

def js_to_json(s):
    return re.sub(r'([{,])\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*:', r'\1"\2":', s)

scraped_at = datetime.now(timezone.utc).isoformat()

components = {
    'TestcasesSdJwtVc':  ('sd-jwt-vc.json',  'https://signature-plugtests.etsi.org/testcases/tc_sd-jwt-vc'),
    'TestcasesNegative': ('negative.json',    'https://signature-plugtests.etsi.org/testcases/tc_negative'),
    'TestcasesIso':      ('iso-mdoc.json',    'https://signature-plugtests.etsi.org/testcases/iso'),
}

for component, (filename, source_url) in components.items():
    pos = content.find(f'__name:"{component}"')
    if pos == -1:
        print(f'  WARNING: {component} not found in JS bundle', flush=True)
        continue
    setup_pos = content.find('setup(e){const t=', pos)
    js_arr = extract_testcases_array(content, setup_pos)
    if not js_arr:
        print(f'  WARNING: could not extract array for {component}', flush=True)
        continue
    sheets = json.loads(js_to_json(js_arr))
    out = {'scraped_at': scraped_at, 'source': source_url, 'sheets': sheets}
    path = os.path.join(testcases_dir, filename)
    with open(path, 'w') as f:
        json.dump(out, f, indent=2)
    total = sum(len(s['testcases']) for s in sheets)
    print(f'  {filename}: {len(sheets)} sheets, {total} test cases', flush=True)

print('Done', flush=True)
PYEOF

rm -f "$JS_TMP"

# ── Rebuild test-cases.json from scraped files ────────────────────────────────
echo "Rebuilding test-cases.json..."
python3 - "$TESTCASES_DIR" "$TEST_CASES_JSON" << 'PYEOF'
import sys, json, os
from datetime import datetime, timezone

testcases_dir  = sys.argv[1]
out_path       = sys.argv[2]

def scraped_tc_to_model(tc, subtitle):
    sections = []
    for key, title in [
        ('protectedHeader', 'Protected Header'),
        ('cwtClaims',       'CWT-claims (15)'),
        ('payload',         'Payload'),
        ('namespace',       'NameSpace'),
        ('signature',       'Signature'),
    ]:
        if key in tc:
            sections.append({'title': title, 'items': tc[key]})
    return {
        'id':          tc['name'].strip().split('  ')[0].strip(),
        'name':        tc['name'].strip().split('  ')[0].strip(),
        'subtitle':    subtitle,
        'description': tc.get('description', ''),
        'sections':    sections,
    }

def load(filename):
    with open(os.path.join(testcases_dir, filename)) as f:
        return json.load(f)

sd_jwt   = load('sd-jwt-vc.json')
negative = load('negative.json')
iso_mdoc = load('iso-mdoc.json')

formats = []
for data, fmt_id, fmt_name in [
    (sd_jwt,   'sd-jwt-vc',          'SD-JWT-VC Test Cases'),
    (negative, 'sd-jwt-vc-negative', 'SD-JWT-VC Negative Test Cases'),
    (iso_mdoc, 'mdoc',               'ISO mdoc Test Cases'),
]:
    profiles = []
    for sheet in data['sheets']:
        pid = sheet['sheetName'].strip()
        profiles.append({
            'id':         pid,
            'notes':      sheet.get('profileText', ''),
            'test_cases': [scraped_tc_to_model(tc, pid) for tc in sheet['testcases']],
        })
    formats.append({'id': fmt_id, 'name': fmt_name, 'url': data['source'], 'profiles': profiles})

with open(out_path, 'w') as f:
    json.dump({'scraped_at': datetime.now(timezone.utc).isoformat(), 'formats': formats}, f, indent=2)

total = sum(len(p['test_cases']) for fmt in formats for p in fmt['profiles'])
print(f'  Wrote {out_path} ({total} test cases total)', flush=True)
PYEOF

echo ""
echo "Done. Scraped files in: $TESTCASES_DIR"
echo "Updated: $TEST_CASES_JSON"
