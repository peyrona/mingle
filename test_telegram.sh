#!/bin/bash
set -euo pipefail

CHAT_ID="-336373800"
BOT_TOKEN="531072848:AAGJ2X7lIWwpD5HNvpEXeKSkmOH2TThOIFc"

curl -X POST \
     -H 'Content-Type: application/json' \
     -d "{\"chat_id\": \"${CHAT_ID}\", \"text\": \"Mesaje de prueba.\"}" \
     https://api.telegram.org/bot$BOT_TOKEN/sendMessage