#!/usr/bin/env bash
# End-to-end check of a running stack, through the nginx front door: health, signup, login, post, feed, logout.
set -euo pipefail

BASE="${1:-http://localhost:${BIRDY_PORT:-8080}}"
USER="smoke$(date +%s)"
PASS="correct horse battery"

say() { printf '%-28s %s\n' "$1" "$2"; }
field() { python3 -c "import json,sys; print(json.load(sys.stdin)$1)"; }

page=$(curl -fsS "$BASE/")
grep -q '<div id="root">' <<<"$page"
say "GET /" "single-page app served"

health=$(curl -fsS "$BASE/api/health")
[ "$(field "['status']" <<<"$health")" = "UP" ]
say "GET /api/health" "$health"

code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/posts")
[ "$code" = "401" ]
say "GET /api/posts (no token)" "$code"

curl -fsS -H 'Content-Type: application/json' -d "{\"username\":\"$USER\",\"email\":\"$USER@example.org\",\"password\":\"$PASS\",\"birthday\":\"1999-01-01\"}" "$BASE/api/users" >/dev/null
say "POST /api/users" "created $USER"

token=$(curl -fsS -H 'Content-Type: application/json' -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" "$BASE/api/sessions" | field "['token']")
say "POST /api/sessions" "token of ${#token} characters"
auth=(-H "Authorization: Bearer $token")

post_id=$(curl -fsS "${auth[@]}" -H 'Content-Type: application/json' -d '{"content":"hello from the smoke test"}' "$BASE/api/posts" | field "['id']")
say "POST /api/posts" "$post_id"

first=$(curl -fsS "${auth[@]}" "$BASE/api/posts?feed=home" | field "[0]['id']")
[ "$first" = "$post_id" ]
say "GET /api/posts?feed=home" "newest post is $first"

curl -fsS "${auth[@]}" -X DELETE "$BASE/api/users/me" >/dev/null
code=$(curl -s -o /dev/null -w '%{http_code}' "${auth[@]}" "$BASE/api/users/me")
[ "$code" = "401" ]
say "DELETE /api/users/me" "account removed, token now $code"

echo "smoke test passed"
