#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
BUILD_PROPS="$SCRIPT_DIR/build.properties"

# ─── No .env found → skip deploy ─────────────────────────────────────────────
if [ ! -f "$ENV_FILE" ]; then
  echo "⏭️  No .env file found → skipping deploy"
  exit 0
fi

# ─── Load .env ────────────────────────────────────────────────────────────────
source "$ENV_FILE"

# ─── DEPLOY_ENABLED is not true → skip deploy ────────────────────────────────
if [ "$DEPLOY_ENABLED" != "true" ]; then
  echo "⏭️  DEPLOY_ENABLED is not true → skipping deploy"
  exit 0
fi

# ─── Read plugin.path and version from build.properties ──────────────────────
PLUGIN_PATH=$(grep "^plugin.path=" "$BUILD_PROPS" | cut -d'=' -f2)
PLUGIN_VERSION=$(grep "^plugin.version=" "$BUILD_PROPS" | cut -d'=' -f2)

if [ -z "$PLUGIN_PATH" ]; then
  echo "❌ Could not read plugin.path from build.properties"
  exit 1
fi

ZIP_FILE="$SCRIPT_DIR/build/package_install/${PLUGIN_PATH}-v${PLUGIN_VERSION}.zip"

if [ ! -f "$ZIP_FILE" ]; then
  echo "❌ ZIP not found: $ZIP_FILE"
  exit 1
fi

echo "🚀 Starting deploy: $(basename $ZIP_FILE)"
echo "   Plugin path: $PLUGIN_PATH"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

idx=1
for SERVER in "${SERVERS[@]}"; do
  IFS='|' read -r NAME TYPE URL_OR_FOLDER USER PASS <<< "$SERVER"

  echo ""
  echo "📡 [Server $idx] $NAME ($TYPE)"

  # ── Deploy type: FOLDER ───────────────────────────────────────────────────
  if [ "$TYPE" = "folder" ]; then
    FOLDER="$URL_OR_FOLDER"
    DEST="$FOLDER/extensions"
    TEMP_DIR=$(mktemp -d)

    if [ ! -d "$FOLDER" ]; then
      echo "  ❌ Mirth root folder not found: $FOLDER"
      rm -rf "$TEMP_DIR"
      ((idx++)); continue
    fi

    # 1. Unzip to temp folder
    unzip -o "$ZIP_FILE" -d "$TEMP_DIR" > /dev/null

    # 2. Check plugin folder exists inside zip
    if [ ! -d "$TEMP_DIR/$PLUGIN_PATH" ]; then
      echo "  ❌ Plugin folder not found in zip: $PLUGIN_PATH"
      rm -rf "$TEMP_DIR"
      ((idx++)); continue
    fi

    # 3. Remove old plugin folder, then copy new one
    rm -rf "$DEST/$PLUGIN_PATH"
    mkdir -p "$DEST"
    cp -r "$TEMP_DIR/$PLUGIN_PATH" "$DEST/"
    rm -rf "$TEMP_DIR"

    echo "  ✅ Copied to: $DEST/$PLUGIN_PATH"

  # ── Deploy type: API ──────────────────────────────────────────────────────
  elif [ "$TYPE" = "api" ]; then
    URL="$URL_OR_FOLDER"

    HTTP_STATUS=$(curl -sk -o /tmp/mirth-response-$idx.txt -w "%{http_code}" \
      -X POST "$URL/api/extensions/_install" \
      -H "X-Requested-With: XMLHttpRequest" \
      -u "$USER:$PASS" \
      -F "file=@$ZIP_FILE")

    if [ "$HTTP_STATUS" = "200" ] || [ "$HTTP_STATUS" = "204" ]; then
      echo "  ✅ Upload successful: $URL"
    else
      echo "  ❌ Upload failed (HTTP $HTTP_STATUS)"
      cat /tmp/mirth-response-$idx.txt
    fi

    rm -f /tmp/mirth-response-$idx.txt
  fi

  ((idx++))
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Deploy completed!"
