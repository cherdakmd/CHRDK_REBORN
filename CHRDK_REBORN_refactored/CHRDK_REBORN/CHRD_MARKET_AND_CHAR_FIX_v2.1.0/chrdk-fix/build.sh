#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════
#  CHRDK_REBORN — Скрипт сборки v2.1.0 (без Gradle)
#  ─────────────────────────────────────────────────────────────────────
#  Использование:
#      ./build.sh                # собрать оба модуля
#      ./build.sh market         # только market
#      ./build.sh artifacts      # только artifacts
#      ./build.sh clean          # очистить
#
#  Требования: Java 17+, Spigot API 1.16.5 (или новее) в classpath.
#  Загрузить API: https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/1.16.5-R0.1-SNAPSHOT/
# ═══════════════════════════════════════════════════════════════════════════

set -e

# ─── Настройки ───
JAVA_VERSION="17"
SPIGOT_API="spigot-api-1.16.5-R0.1-SNAPSHOT.jar"
VKCHAT_CORE="vkchat_core-1.0.0.jar"
DIST_DIR="dist"

mkdir -p "$DIST_DIR"

build_module() {
    local module=$1
    local main_class=$2
    local jar_name=$3
    local src_dir="$module/src/main/java"
    local res_dir="$module/src/main/resources"
    local out_dir="build/$module"
    local classes_dir="$out_dir/classes"
    local jar_path="$DIST_DIR/$jar_name"

    echo ""
    echo "═══════════════════════════════════════════════════════"
    echo "  Сборка модуля: $module"
    echo "═══════════════════════════════════════════════════════"

    if [ ! -f "$SPIGOT_API" ]; then
        echo "❌ Не найден $SPIGOT_API"
        echo "   Скачайте: https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/1.16.5-R0.1-SNAPSHOT/maven-metadata.xml"
        exit 1
    fi

    rm -rf "$out_dir"
    mkdir -p "$classes_dir"

    # Копируем ресурсы
    if [ -d "$res_dir" ]; then
        cp -r "$res_dir"/* "$classes_dir/" 2>/dev/null || true
    fi

    # Компилируем
    echo "▶ Компиляция Java $JAVA_VERSION..."
    find "$src_dir" -name "*.java" > "$out_dir/sources.txt"
    javac --release $JAVA_VERSION \
        -cp "$SPIGOT_API:$VKCHAT_CORE" \
        -d "$classes_dir" \
        @"$out_dir/sources.txt" 2>&1 | tee "$out_dir/compile.log"

    if [ ${PIPESTATUS[0]} -ne 0 ]; then
        echo "❌ Ошибка компиляции. Смотрите build/$module/compile.log"
        exit 1
    fi

    # Собираем jar
    echo "▶ Создание JAR: $jar_name..."
    cd "$classes_dir"
    jar cf "../../../$jar_path" .
    cd - > /dev/null

    if [ -f "$jar_path" ]; then
        SIZE=$(du -h "$jar_path" | cut -f1)
        echo "✅ $jar_name ($SIZE)"
    else
        echo "❌ Ошибка сборки JAR"
        exit 1
    fi
}

case "${1:-all}" in
    market)
        build_module "vkchat_market" \
            "ru.example.vkchatmarket.VKChatMarketPluginV2" \
            "VKChatMarket-2.1.0.jar"
        ;;
    artifacts)
        build_module "vkchat_artifacts" \
            "ru.example.vkchatartifacts.VKChatArtifactsPluginV2" \
            "VKChatArtifacts-2.1.0.jar"
        ;;
    clean)
        echo "🧹 Очистка..."
        rm -rf build dist
        echo "✅ Готово"
        ;;
    all|"")
        build_module "vkchat_market" \
            "ru.example.vkchatmarket.VKChatMarketPluginV2" \
            "VKChatMarket-2.1.0.jar"
        build_module "vkchat_artifacts" \
            "ru.example.vkchatartifacts.VKChatArtifactsPluginV2" \
            "VKChatArtifacts-2.1.0.jar"
        ;;
    *)
        echo "Использование: $0 [all|market|artifacts|clean]"
        exit 1
        ;;
esac

echo ""
echo "═══════════════════════════════════════════════════════"
echo "  Сборка завершена!"
echo "═══════════════════════════════════════════════════════"
ls -la "$DIST_DIR"
