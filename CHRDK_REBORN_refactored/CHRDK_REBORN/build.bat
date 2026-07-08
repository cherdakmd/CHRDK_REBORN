@echo off
chcp 65001 >nul
echo ========================================
echo   CHRDK REBORN - Сборка плагинов
echo ========================================
echo.

if not exist "gradlew.bat" (
    echo [ОШИБКА] Gradle Wrapper не найден!
    pause
    exit /b 1
)

echo [1/4] Очистка предыдущих сборок...
call gradlew.bat clean --quiet
if %ERRORLEVEL% NEQ 0 (
    echo [ОШИБКА] Ошибка при очистке!
    pause
    exit /b 1
)
echo [OK] Очистка завершена.

echo.
echo [2/4] Сборка плагинов...
call gradlew.bat build -x test --quiet
if %ERRORLEVEL% NEQ 0 (
    echo [ОШИБКА] Ошибка при сборке!
    pause
    exit /b 1
)
echo [OK] Сборка завершена.

echo.
echo [3/4] Копирование JAR файлов...
if not exist "plugins_output" mkdir plugins_output

for /d %%d in (vkchat_*) do (
    if exist "%%d\build\libs\*.jar" (
        copy /Y "%%d\build\libs\*.jar" "plugins_output\" >nul
        echo [OK] Скопирован: %%d
    )
)

echo.
echo [4/4] Создание архива...
cd plugins_output
powershell -Command "Compress-Archive -Path *.jar -DestinationPath ..\CHRDK_REBORN_plugins.zip -Force"
cd ..
echo [OK] Архив создан: CHRDK_REBORN_plugins.zip

echo.
echo ========================================
echo   Сборка успешно завершена!
echo ========================================
echo.
echo JAR файлы: plugins_output\
echo Архив: CHRDK_REBORN_plugins.zip
pause
