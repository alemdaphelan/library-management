param(
    [switch]$SkipComposeValidation
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$failures = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()

function Add-Failure([string]$message) {
    $failures.Add($message)
    Write-Host "[FAIL] $message" -ForegroundColor Red
}

function Add-Warning([string]$message) {
    $warnings.Add($message)
    Write-Host "[WARN] $message" -ForegroundColor Yellow
}

function Add-Pass([string]$message) {
    Write-Host "[PASS] $message" -ForegroundColor Green
}

Push-Location $repositoryRoot
try {
    $requiredFiles = @(
        ".env.example",
        "docker-compose.yml",
        "README.md",
        "backend/README.md",
        "TestChucNang/README.md",
        "TestChucNang/01_HUONG_DAN_DUNG_MOI_TRUONG_TEST.md",
        "TestChucNang/02_CHECKLIST_TEST_TOAN_BO_HE_THONG.md",
        "TestChucNang/03_MAU_BAO_LOI.md",
        "TestChucNang/04_BIEN_BAN_NGHIEM_THU.md",
        "TestChucNang/05_THONG_TIN_BAN_GIAO_12D.md",
        "Word_PPTX/README.md",
        "Word_PPTX/DANH_MUC_TAI_LIEU.md",
        "Docs/TUAN_12C_GOI_BAN_GIAO_MINH_CHUNG_KY_THUAT.md",
        "Docs/TUAN_12D_CHOT_REPOSITORY_VA_BAN_GIAO.md"
    )

    $missingFiles = @($requiredFiles | Where-Object { -not (Test-Path -LiteralPath $_ -PathType Leaf) })
    if ($missingFiles.Count -eq 0) {
        Add-Pass "Đủ các file cấu hình và bàn giao bắt buộc."
    } else {
        foreach ($file in $missingFiles) {
            Add-Failure "Thiếu file bắt buộc: $file"
        }
    }

    & git check-ignore --quiet -- .env
    if ($LASTEXITCODE -eq 0) {
        Add-Pass ".env được Git bỏ qua."
    } else {
        Add-Failure ".env chưa được Git bỏ qua."
    }

    $trackedPaths = @(& git ls-files)
    $forbiddenTracked = @($trackedPaths | Where-Object {
        $_ -match '(^|/)(target|build|node_modules|\.codex-tmp|backups)/' -or
        $_ -match '(^|/)\.env($|\.)' -and $_ -ne '.env.example'
    })
    if ($forbiddenTracked.Count -eq 0) {
        Add-Pass "Không theo dõi .env, thư mục build, backup hoặc file tạm."
    } else {
        foreach ($file in $forbiddenTracked) {
            Add-Failure "File không được phép đang được Git theo dõi: $file"
        }
    }

    $candidatePaths = @(& git ls-files --cached --others --exclude-standard)
    $textExtensions = @(
        ".css", ".dart", ".html", ".java", ".json", ".md", ".mjs",
        ".properties", ".ps1", ".ts", ".txt", ".xml", ".yaml", ".yml"
    )
    $secretPatterns = @(
        'AQ\.[A-Za-z0-9_-]{30,}',
        'AIza[0-9A-Za-z_-]{20,}',
        'gh[pousr]_[A-Za-z0-9]{30,}',
        'AKIA[0-9A-Z]{16}',
        '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----'
    )
    $secretFiles = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)

    foreach ($relativePath in $candidatePaths) {
        if (-not (Test-Path -LiteralPath $relativePath -PathType Leaf)) {
            continue
        }
        $extension = [System.IO.Path]::GetExtension($relativePath).ToLowerInvariant()
        if ($relativePath -ne ".env.example" -and $extension -notin $textExtensions) {
            continue
        }
        $fileInfo = Get-Item -LiteralPath $relativePath
        if ($fileInfo.Length -gt 5MB) {
            Add-Warning "Bỏ qua quét nội dung file văn bản trên 5 MB: $relativePath"
            continue
        }
        $content = Get-Content -LiteralPath $relativePath -Raw -ErrorAction SilentlyContinue
        foreach ($pattern in $secretPatterns) {
            if ($content -match $pattern) {
                $null = $secretFiles.Add($relativePath)
            }
        }
    }

    if ($secretFiles.Count -eq 0) {
        Add-Pass "Không phát hiện mẫu API key, access token hoặc private key trong file sẽ bàn giao."
    } else {
        foreach ($file in $secretFiles) {
            Add-Failure "Phát hiện mẫu secret trong file: $file"
        }
    }

    $largeTrackedFiles = foreach ($relativePath in $trackedPaths) {
        if (Test-Path -LiteralPath $relativePath -PathType Leaf) {
            $fileInfo = Get-Item -LiteralPath $relativePath
            if ($fileInfo.Length -gt 50MB) {
                [PSCustomObject]@{ Path = $relativePath; SizeMB = [math]::Round($fileInfo.Length / 1MB, 1) }
            }
        }
    }
    foreach ($file in $largeTrackedFiles) {
        Add-Warning "File Git lớn hơn 50 MB: $($file.Path) ($($file.SizeMB) MB)."
    }

    if (-not $SkipComposeValidation) {
        & docker compose --env-file .env.example config --quiet
        if ($LASTEXITCODE -eq 0) {
            Add-Pass "docker-compose.yml hợp lệ với .env.example."
        } else {
            Add-Failure "docker compose config thất bại với .env.example."
        }
    }

    Write-Host ""
    Write-Host "Preflight 12D: $($failures.Count) lỗi, $($warnings.Count) cảnh báo."
    if ($failures.Count -gt 0) {
        exit 1
    }
} finally {
    Pop-Location
}
