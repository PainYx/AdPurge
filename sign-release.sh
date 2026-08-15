#!/bin/sh
# AdPurge 发布签名脚本：zipalign + apksigner 三重签名（v1+v2+v3）
# 用法：先 ./gradlew assembleRelease，再 sh sign-release.sh
# 前提：本机有 Android build-tools 的 zipalign/apksigner 与 JDK keytool
# keystore: app/release.keystore  alias: adpurge
# 密码见根目录 gradle.properties（RELEASE_STORE_PASSWORD / RELEASE_KEY_PASSWORD）

set -x
BT="$1"
[ -z "$BT" ] && BT="/root/Android/build-tools/35.0.0"
IN="app/build/outputs/apk/release/app-release.apk"
OUT="app/build/outputs/apk/release/AdPurge-v5.0-signed.apk"

STORE_PW=$(grep '^RELEASE_STORE_PASSWORD=' gradle.properties | cut -d= -f2)
KEY_PW=$(grep '^RELEASE_KEY_PASSWORD=' gradle.properties | cut -d= -f2)

"$BT/zipalign" -p -f 4 "$IN" /tmp/adpurge-aligned.apk
"$BT/apksigner" sign \
    --ks app/release.keystore \
    --ks-key-alias adpurge \
    --ks-pass "pass:$STORE_PW" \
    --key-pass "pass:$KEY_PW" \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "$OUT" \
    /tmp/adpurge-aligned.apk

"$BT/apksigner" verify --verbose "$OUT"
echo "完成: $OUT"
