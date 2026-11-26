# Creates backend/.env for LOCAL development using hidden prompts.
# - Secrets are typed with hidden input and are never echoed, logged or passed on a command line.
# - JWT_SECRET is generated randomly (48 bytes) so nobody needs to see or type it.
# - Refuses to overwrite an existing backend/.env.
#
# Run from the repository root in your own PowerShell window (prompts need an interactive terminal):
#   powershell -ExecutionPolicy Bypass -File backend\scripts\new-local-env.ps1

$ErrorActionPreference = 'Stop'
$envPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\.env'))

if (Test-Path -LiteralPath $envPath) {
    Write-Host "$envPath already exists - not overwriting it. Edit it directly if values need to change."
    exit 1
}

function Read-SecretText([string]$Prompt) {
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

function Read-PlainText([string]$Prompt, [string]$Default) {
    $value = Read-Host -Prompt "$Prompt [$Default]"
    if ([string]::IsNullOrWhiteSpace($value)) { return $Default }
    return $value.Trim()
}

# Java properties escaping: backslash, leading space, and non-ASCII (as \uXXXX) so the file is plain ASCII.
function ConvertTo-PropertyValue([string]$Value) {
    $builder = New-Object System.Text.StringBuilder
    $index = 0
    foreach ($char in $Value.ToCharArray()) {
        $code = [int]$char
        if ($char -eq '\') { [void]$builder.Append('\\') }
        elseif ($index -eq 0 -and $char -eq ' ') { [void]$builder.Append('\ ') }
        elseif ($code -lt 32 -or $code -gt 126) { [void]$builder.Append(('\u{0:x4}' -f $code)) }
        else { [void]$builder.Append($char) }
        $index++
    }
    return $builder.ToString()
}

Write-Host "FarmCrop local configuration (values are stored only in backend/.env, which Git ignores)."
Write-Host ""
$dbUser = Read-PlainText 'MySQL username' 'root'
$dbPassword = Read-SecretText 'MySQL password (hidden)'

Write-Host ""
Write-Host "Initial admin account (created once on first start; leave username empty to skip)."
$adminUser = Read-PlainText 'Admin username (3-30 lower-case letters/digits/._)' ''
$adminEmail = ''
$adminPassword = ''
if ($adminUser -ne '') {
    $adminEmail = Read-PlainText 'Admin email' ''
    while ($true) {
        $adminPassword = Read-SecretText 'Admin password, 12-72 characters (hidden)'
        $confirm = Read-SecretText 'Confirm admin password (hidden)'
        if ($adminPassword -ne $confirm) { Write-Host 'Passwords do not match. Try again.'; continue }
        if ($adminPassword.Length -lt 12 -or $adminPassword.Length -gt 72) { Write-Host 'Use 12-72 characters. Try again.'; continue }
        break
    }
}

$bytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
$jwtSecret = [Convert]::ToBase64String($bytes)

$lines = @(
    '# Local development secrets - created by backend/scripts/new-local-env.ps1. Never commit this file.',
    'DB_HOST=localhost',
    'DB_PORT=3306',
    'DB_NAME=farmcrop',
    "DB_USERNAME=$(ConvertTo-PropertyValue $dbUser)",
    "DB_PASSWORD=$(ConvertTo-PropertyValue $dbPassword)",
    "JWT_SECRET=$jwtSecret",
    "FARMCROP_ADMIN_USERNAME=$(ConvertTo-PropertyValue $adminUser)",
    "FARMCROP_ADMIN_EMAIL=$(ConvertTo-PropertyValue $adminEmail)",
    "FARMCROP_ADMIN_PASSWORD=$(ConvertTo-PropertyValue $adminPassword)",
    'RAZORPAY_KEY_ID=',
    'RAZORPAY_KEY_SECRET=',
    'RAZORPAY_WEBHOOK_SECRET=',
    'AI_PROVIDER=',
    'AI_PROVIDER_API_KEY=',
    'CORS_ALLOWED_ORIGINS=http://localhost:5173'
)
[IO.File]::WriteAllText($envPath, ($lines -join "`r`n") + "`r`n", [Text.Encoding]::ASCII)

$dbPassword = $null; $adminPassword = $null; $confirm = $null; $jwtSecret = $null
Write-Host ""
Write-Host "Created $envPath (values not shown). Tell Claude it is ready."
