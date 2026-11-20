# Automated build script for rust_dex2c native shared libraries
$ErrorActionPreference = "Stop"

Write-Host "==> Compiling rust_dex2c for aarch64-linux-android (arm64-v8a)..."
Push-Location "rust-engine"
try {
    cargo build --target aarch64-linux-android --release
    Write-Host "==> Compiling rust_dex2c for x86_64-linux-android (x86_64)..."
    cargo build --target x86_64-linux-android --release
} finally {
    Pop-Location
}

Write-Host "==> Deploying shared libraries to app/src/main/jniLibs..."
New-Item -ItemType Directory -Path "app\src\main\jniLibs\arm64-v8a", "app\src\main\jniLibs\x86_64" -Force | Out-Null
Copy-Item "rust-engine\target\aarch64-linux-android\release\librust_dex2c.so" "app\src\main\jniLibs\arm64-v8a\librust_dex2c.so" -Force
Copy-Item "rust-engine\target\x86_64-linux-android\release\librust_dex2c.so" "app\src\main\jniLibs\x86_64\librust_dex2c.so" -Force

Write-Host "==> Rust native backend successfully built and deployed to jniLibs!"
Get-ChildItem -Path "app\src\main\jniLibs" -Recurse -Filter "*.so" | Select-Object FullName, Length
