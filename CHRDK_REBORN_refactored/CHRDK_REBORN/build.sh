#!/bin/bash

echo "========================================"
echo "  CHRDK REBORN - Сборка плагинов"
echo "========================================"
echo

if [ ! -f "./gradlew" ]; then
    echo "[ОШИБКА] Gradle Wrapper не найден!"
    exit 1
fi

chmod +x ./gradlew

echo "[1/4] Очистка предыдущих сборок..."
./gradlew clean --quiet
if [ $? -ne 0 ]; then
    echo "[ОШИБКА] Ошибка при очистке!"
    exit 1
fi
echo "[OK] Очистка завершена."

echo
echo "[2/4] Сборка плагинов..."
./gradlew build -x test --quiet
if [ $? -ne 0 ]; then
    echo "[ОШИБКА] Ошибка при сборке!"
    exit 1
fi
echo "[OK] Сборка завершена."

echo
echo "[3/4] Копирование JAR файлов..."
mkdir -p plugins_output

for dir in vkchat_*; do
    if [ -d "$dir/build/libs" ]; then
        cp "$dir/build/libs/"*.jar plugins_output/ 2>/dev/null
        echo "[OK] Скопирован: $dir"
    fi
done

echo
echo "[4/4] Создание архива..."
cd plugins_output
zip -r ../CHRDK_REBORN_plugins.zip *.jar 2>/dev/null
cd ..
echo "[OK] Архив создан: CHRDK_REBORN_plugins.zip"

echo
echo "========================================"
echo "  Сборка успешно завершена!"
echo "========================================"
echo
echo "JAR файлы: plugins_output/"
echo "Архив: CHRDK_REBORN_plugins.zip"
