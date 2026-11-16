# Cria a chave de assinatura do app e cadastra todos os Secrets no GitHub.
# Rode UMA vez, depois de "gh auth login" e de o repositório existir:
#   powershell -ExecutionPolicy Bypass -File scripts\configurar-assinatura.ps1
#
# A chave e a senha ficam em Documentos\Flynn-chave-assinatura.
# FAÇA BACKUP dessa pasta (pendrive, Google Drive...). Sem ela não dá para publicar atualizações.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

$java = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { (Get-ChildItem "$env:ProgramFiles\Eclipse Adoptium" -Directory | Select-Object -First 1).FullName }
$keytool = Join-Path $java 'bin\keytool.exe'
$gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
if (-not $gh) { $gh = "$env:ProgramFiles\GitHub CLI\gh.exe" }

$repo = & $gh repo view --json nameWithOwner -q .nameWithOwner
if (-not $repo) { throw "Não achei o repositório. Rode este script dentro da pasta flynn-app, depois de criar o repo." }
Write-Host "Repositório: $repo"

$gs = Join-Path $root 'app\google-services.json'
if (-not (Test-Path $gs)) { throw "Falta $gs" }

$dir = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Flynn-chave-assinatura'
New-Item -ItemType Directory -Force $dir | Out-Null
$ks = Join-Path $dir 'flynn.jks'
$passFile = Join-Path $dir 'senha-da-chave.txt'

if (Test-Path $ks) {
    Write-Host "Chave já existe em $ks; reaproveitando."
    $pass = (Get-Content $passFile | Select-Object -First 1).Trim()
} else {
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $pass = ([Convert]::ToBase64String($bytes)) -replace '[+/=]', 'x'
    & $keytool -genkeypair -keystore $ks -storetype PKCS12 -alias flynn -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $pass -keypass $pass -dname "CN=Flynn, O=Familia, C=BR"
    if ($LASTEXITCODE -ne 0) { throw "keytool falhou" }
    Set-Content -Path $passFile -Encoding ascii -Value @($pass, "alias: flynn", "Guarde esta pasta em local seguro.")
    Write-Host "Chave criada em $ks"
}

Write-Host "Cadastrando Secrets no GitHub..."
& $gh secret set KEYSTORE_BASE64 --repo $repo --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes($ks)))
& $gh secret set KEYSTORE_PASSWORD --repo $repo --body $pass
& $gh secret set KEY_PASSWORD --repo $repo --body $pass
& $gh secret set KEY_ALIAS --repo $repo --body 'flynn'
Get-Content $gs -Raw | & $gh secret set GOOGLE_SERVICES_JSON --repo $repo

& $gh secret list --repo $repo
Write-Host ""
Write-Host "Pronto! Faça backup da pasta: $dir" -ForegroundColor Green
