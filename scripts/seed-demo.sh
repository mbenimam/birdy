#!/usr/bin/env bash
# Fills a running stack with a few demo accounts, follows, posts, likes and comments.
# Every account uses the password printed at the end. Safe to run once on an empty stack.
set -euo pipefail

BASE="${1:-http://localhost:${BIRDY_PORT:-8080}}"
PASS="birdy-demo-2026"
json=(-H 'Content-Type: application/json')
field() { python3 -c "import json,sys; print(json.load(sys.stdin)$1)"; }

declare -A token
for name in ada linus grace alan; do
  curl -fsS "${json[@]}" -d "{\"username\":\"$name\",\"email\":\"$name@example.org\",\"password\":\"$PASS\",\"birthday\":\"1990-01-01\"}" "$BASE/api/users" >/dev/null
  token[$name]=$(curl -fsS "${json[@]}" -d "{\"username\":\"$name\",\"password\":\"$PASS\"}" "$BASE/api/sessions" | field "['token']")
done

as() { local who=$1; shift; curl -fsS -H "Authorization: Bearer ${token[$who]}" "$@"; }
follow() { as "$1" -X PUT "$BASE/api/users/me/following/$2" >/dev/null; }
post() { as "$1" "${json[@]}" -d "{\"content\":\"$2\"}" "$BASE/api/posts" | field "['id']"; }
like() { as "$1" -X PUT "$BASE/api/posts/$2/like" >/dev/null; }
comment() { as "$1" "${json[@]}" -d "{\"content\":\"$3\"}" "$BASE/api/posts/$2/comments" >/dev/null; }

follow ada linus; follow ada grace; follow linus ada; follow grace ada; follow alan ada; follow grace alan

p1=$(post grace "Found a moth in the relay today. Taped it in the logbook: first actual bug.")
p2=$(post linus "Talk is cheap. Show me the code.")
p3=$(post alan "Can a machine be said to think? Asking for a paper.")
p4=$(post ada "The engine weaves algebraic patterns just as the loom weaves flowers and leaves.")
p5=$(post ada "Users in MySQL, posts in MongoDB, chat over a WebSocket. One compose file starts all of it.")

like ada "$p1"; like linus "$p1"; like alan "$p1"; like ada "$p2"; like grace "$p4"; like linus "$p5"; like grace "$p5"
comment ada "$p1" "Keep the logbook."
comment linus "$p1" "Did it reproduce?"
comment grace "$p5" "And the tests run against the real databases?"
comment ada "$p5" "Yes, in containers."

echo "seeded: ada, linus, grace, alan (password: $PASS)"
