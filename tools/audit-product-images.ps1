param([string]$ProductCsv, [string]$UploadDirectory)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path -Parent $PSScriptRoot
$staticRoot = Join-Path $projectRoot 'src/main/resources/static'
if (-not $UploadDirectory) {
    $UploadDirectory = if ($env:LUNA_IMAGE_DIR) { $env:LUNA_IMAGE_DIR } else { Join-Path $projectRoot 'data/product-images' }
}
$imageRoot = Join-Path $staticRoot 'images'
$inventoryPath = Join-Path $projectRoot 'docs/product-image-inventory.csv'
$inventory = Import-Csv -LiteralPath $inventoryPath -Encoding UTF8
$issues = 0

# 原本の欠落と変更を読み取り専用で検出する。台帳や画像は更新しない。
foreach ($entry in $inventory) {
    $filePath = Join-Path $staticRoot $entry.path.TrimStart('/')
    if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) {
        Write-Warning "Missing original: $($entry.path)"
        $issues++
    } elseif ((Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash -ne $entry.sha256) {
        Write-Warning "Changed original: $($entry.path)"
        $issues++
    }
}

$files = @(Get-ChildItem -LiteralPath $imageRoot -Recurse -File)
$hashes = @{}
foreach ($file in $files) {
    $path = '/images/' + $file.FullName.Substring($imageRoot.Length + 1).Replace('\', '/')
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
    if ($hashes.ContainsKey($hash)) {
        Write-Warning "Identical files: $path / $($hashes[$hash])"
    } else { $hashes[$hash] = $path }

    # SVG は共通画像のみ使用する。WebP の寸法確認は別途対応するデコーダーで行う。
    $dimensions = 'not inspected'
    $ratio = ''
    if ($file.Extension -match '^\.(png|jpg|jpeg)$') {
        $image = [System.Drawing.Image]::FromFile($file.FullName)
        try {
            $dimensions = "$($image.Width)x$($image.Height)"
            if ($path.StartsWith('/images/products/')) {
                $ratio = if ($image.Width * 4 -eq $image.Height * 3) { '3:4' } else { 'legacy ratio' }
            }
        } finally { $image.Dispose() }
    }
    [PSCustomObject]@{ Path=$path; Dimensions=$dimensions; Ratio=$ratio; Bytes=$file.Length }
}

# 別途取得した商品一覧の CSV のみを照合し、DB 接続や商品更新は行わない。
if ($ProductCsv) {
    $products = @(Import-Csv -LiteralPath $ProductCsv -Encoding UTF8)
    if ($products.Count -gt 0 -and
            (@('id','name','image_url') | Where-Object { $_ -notin $products[0].PSObject.Properties.Name })) {
        throw 'CSV must contain id,name,image_url columns.'
    }
    foreach ($product in $products) {
        $path = $product.image_url
        if ([string]::IsNullOrWhiteSpace($path)) {
            Write-Warning "NO IMAGE: product $($product.id) / $($product.name)"
            continue
        }
        if ($path -notmatch '^/images/[a-zA-Z0-9/_-]+\.(png|jpg|jpeg|webp)$' -and
                $path -ne '/images/common/no-image.svg') {
            Write-Warning "Invalid path: product $($product.id) / $path"
            $issues++
        } else {
            # 外部保存画像も照合するが、保存先の作成や内容変更は行わない。
            $candidate = if ($path.StartsWith('/images/uploads/')) {
                if ($path -notmatch '^/images/uploads/[0-9a-f]{32}\.(jpg|png)$') {
                    Write-Warning "Invalid upload path: product $($product.id) / $path"
                    $issues++
                    continue
                }
                Join-Path $UploadDirectory $path.Substring('/images/uploads/'.Length)
            } else { Join-Path $staticRoot $path.TrimStart('/') }
            if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
                Write-Warning "Missing image: product $($product.id) / $path"
                $issues++
            }
        }
    }
    $products | Group-Object image_url | Where-Object {
        $_.Count -gt 1 -and -not [string]::IsNullOrWhiteSpace($_.Name) -and
        $_.Name -ne '/images/common/no-image.svg'
    } | ForEach-Object {
        Write-Warning "Shared image requires review: $($_.Name) / product IDs: $(($_.Group.id) -join ', ')"
    }
}

Write-Host "Originals: $($inventory.Count); files: $($files.Count); issues: $issues"
if ($issues -gt 0) { exit 1 }
