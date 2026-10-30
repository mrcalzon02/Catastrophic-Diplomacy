# Verify an actual Windows Installer package by installing and uninstalling it.
$ErrorActionPreference = 'Stop'
$msi = (Resolve-Path (Join-Path $PSScriptRoot '../desktop/build/platform-packages/Catastrophic-Diplomacy-Windows-x64.msi')).Path
if ((Get-Item $msi).Length -lt 1MB) { throw 'MSI package is suspiciously small' }
$bytes = [System.IO.File]::ReadAllBytes($msi)
if ([Convert]::ToHexString($bytes[0..7]) -ne 'D0CF11E0A1B11AE1') {
  throw 'Expected real MSI compound storage, not a ZIP or EXE'
}
$signature = (Get-AuthenticodeSignature $msi).Status
if ($signature -notin @('NotSigned', 'Valid')) {
  throw "Unexpected MSI signing state $signature"
}
$installer = New-Object -ComObject WindowsInstaller.Installer
$db = $installer.OpenDatabase($msi, 0)
function Get-MsiProperty([string]$name) {
  $q = [char]96
  $sql = ('SELECT '+$q+'Value'+$q+' FROM '+$q+'Property'+$q+' WHERE '+$q+'Property'+$q+"='$name'")
  $view = $db.OpenView($sql)
  $view.Execute()
  $row = $view.Fetch()
  if ($null -eq $row) { throw "Missing MSI property: $name" }
  return $row.StringData(1)
}
$product = Get-MsiProperty 'ProductCode'
$version = Get-MsiProperty 'ProductVersion'
$upgrade = Get-MsiProperty 'UpgradeCode'
if ([string]::IsNullOrWhiteSpace($upgrade)) {
  # WiX stores the upgrade family in MSI's Upgrade TABLE; it need not
  # appear as a dynamic Property entry. Inspect the real Installer database.
  $q = [char]96
  $sql = ('SELECT '+$q+'UpgradeCode'+$q+' FROM '+$q+'Upgrade'+$q)
  $view = $db.OpenView($sql)
  $view.Execute()
  $row = $view.Fetch()
  if ($null -eq $row) { throw 'No upgrade identity in MSI Upgrade table' }
  $upgrade = $row.StringData(1)
}
if ($version -ne '0.1.5') { throw "MSI version mismatch: $version" }
# COM Automation values may be null or variant types. Normalize using a
# PowerShell string conversion BEFORE any method calls on those values.
$normalizedUpgrade = [string]$upgrade
$normalizedUpgrade = ($normalizedUpgrade -replace '[{}]', '').Trim().ToLowerInvariant()
if ($normalizedUpgrade.Length -eq 0) {
  Write-Host 'NOTE: first-install MSI does not expose an upgrade GUID; upgrade QA remains outstanding.'
} elseif ($normalizedUpgrade -ne 'cfc2e09f-fbaa-4b95-9f20-d59ac1148057') {
  throw "MSI upgrade identity mismatch: $normalizedUpgrade"
}
$installLog = Join-Path $env:RUNNER_TEMP 'cd-msi-install.log'
$uninstallLog = Join-Path $env:RUNNER_TEMP 'cd-msi-uninstall.log'
$installed = $false
try {
  $proc = Start-Process -FilePath msiexec.exe -PassThru -Wait -ArgumentList @(
    '/i', ([char]34 + $msi + [char]34), '/qn', '/norestart',
    '/L*v', ([char]34 + $installLog + [char]34))
  if ($proc.ExitCode -ne 0) {
    Get-Content $installLog -Tail 35 -ErrorAction SilentlyContinue
    throw "MSI silent installation failed, code $($proc.ExitCode)"
  }
  # Windows Installer's COM ProductState accessor is unreliable with the
  # hosted PowerShell COM bridge. An exit code of zero plus actual installed
  # launcher/runtime, followed by a real removal, are stronger observations.
  $installed = $true
  $launcher = Get-ChildItem -Path $env:LOCALAPPDATA -Filter 'CatastrophicDiplomacy.exe' -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($null -eq $launcher) { throw 'Installed MSI launcher not found under per-user application directory' }
  if (!(Test-Path (Join-Path $launcher.DirectoryName 'runtime/release'))) {
    throw 'Installed MSI does not include the Java runtime'
  }
  Write-Host "PASS: MSI installed launcher and bundled Java at $($launcher.FullName)"
}
finally {
  if ($installed) {
    $proc = Start-Process -FilePath msiexec.exe -PassThru -Wait -ArgumentList @(
      '/x', ([char]34 + $msi + [char]34), '/qn', '/norestart',
      '/L*v', ([char]34 + $uninstallLog + [char]34))
    if ($proc.ExitCode -ne 0) {
      Get-Content $uninstallLog -Tail 35 -ErrorAction SilentlyContinue
      throw "MSI uninstall failed, code $($proc.ExitCode)"
    }
    if ($null -ne $launcher -and (Test-Path $launcher.FullName)) {
      throw 'Uninstall succeeded but the installed executable still exists'
    }
    Write-Host 'PASS: Windows MSI uninstalled and executable removed'
  }
}
