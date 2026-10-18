#!/usr/bin/env bash
# =====================================================================
# demo.sh: walks through the six required cases (SPEC.md section 7)
# against the RUNNING app, using only curl, sed, grep and cut.
#
#   1. Successful registration        -> 201
#   2. Duplicate email                -> 409
#   3. Successful login               -> 200 (prints the token)
#   4. Wrong password                 -> 401
#   5. /api/users/me with the token   -> 200
#   6. /api/users/me, tampered token  -> 401
#
# Start the app first (./mvnw spring-boot:run), then run: ./demo.sh
# Each run uses a new email and username (timestamp suffix), so it can be rerun.
# =====================================================================

# Where the app is listening. Override with: BASE_URL=http://host:port ./demo.sh
BASE_URL="${BASE_URL:-http://localhost:8080}"

# Seconds since 1970: unique per run, so this run's account never collides with an earlier one.
RUN_ID="$(date +%s)"
EMAIL="demo_${RUN_ID}@example.com"
USERNAME="demo_${RUN_ID}"
PASSWORD="correct-horse-battery"

# -i: show the status line and headers as well as the body.  -s: hide curl's progress bar.

echo "=== 1. Successful registration ==="
curl -i -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
echo; echo

echo "=== 2. Duplicate email ==="
# Same email, different username, so the 409 can only come from the email constraint.
curl -i -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${USERNAME}_2\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
echo; echo

echo "=== 3. Successful login ==="
LOGIN_RESPONSE="$(curl -i -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
echo "$LOGIN_RESPONSE"
echo; echo

# Pull the token out of the JSON body without jq: grep finds "accessToken":"<value>",
# then cut takes the 4th piece when the match is split on double quotes.
TOKEN="$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)"

echo "=== Access token (paste into jwt.io for the next assignment) ==="
echo "$TOKEN"
echo

echo "=== 4. Wrong password ==="
curl -i -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"wrong-password-123\"}"
echo; echo

echo "=== 5. /api/users/me with the token ==="
curl -i -s "$BASE_URL/api/users/me" \
  -H "Authorization: Bearer $TOKEN"
echo; echo

echo "=== 6. /api/users/me with a tampered token ==="
# A JWT is header.payload.signature. Change the 6th character of the signature
# (not the last one, whose low bits can be padding and might not change anything).
TOKEN_HEADER="$(echo "$TOKEN" | cut -d. -f1)"
TOKEN_PAYLOAD="$(echo "$TOKEN" | cut -d. -f2)"
SIGNATURE="$(echo "$TOKEN" | cut -d. -f3)"
SIGNATURE_START="$(echo "$SIGNATURE" | cut -c1-5)"
SIXTH_CHAR="$(echo "$SIGNATURE" | cut -c6)"
SIGNATURE_REST="$(echo "$SIGNATURE" | cut -c7-)"
if [ "$SIXTH_CHAR" = "A" ]; then
  NEW_CHAR="B"
else
  NEW_CHAR="A"
fi
TAMPERED_TOKEN="${TOKEN_HEADER}.${TOKEN_PAYLOAD}.${SIGNATURE_START}${NEW_CHAR}${SIGNATURE_REST}"
curl -i -s "$BASE_URL/api/users/me" \
  -H "Authorization: Bearer $TAMPERED_TOKEN"
echo
