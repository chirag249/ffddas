@echo off
echo Fixing OpenCV CMake paths...

cd /d C:\Users\chira\AndroidStudioProjects\ffddas\OpenCV\native\jni

for /d %%D in (abi-*) do (
    echo.
    echo Processing %%D...
    
    if exist "%%D\OpenCVConfig.cmake" (
        echo   Fixing OpenCVConfig.cmake
        powershell -NoProfile -Command "(Get-Content '%%D\OpenCVConfig.cmake') -replace '/sdk/native/', '/native/' | Set-Content '%%D\OpenCVConfig.cmake'"
    )
    
    if exist "%%D\OpenCVModules-release.cmake" (
        echo   Fixing OpenCVModules-release.cmake
        powershell -NoProfile -Command "(Get-Content '%%D\OpenCVModules-release.cmake') -replace '/sdk/native/', '/native/' | Set-Content '%%D\OpenCVModules-release.cmake'"
    )
)

echo.
echo Done! All OpenCV CMake files have been fixed.
pause
