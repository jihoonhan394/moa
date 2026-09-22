[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
$requiredVariables = @(
  'SPRING_DATASOURCE_URL',
  'SPRING_DATASOURCE_USERNAME',
  'SPRING_DATASOURCE_PASSWORD',
  'MOA_BOOTSTRAP_ADMIN_PASSWORD'
)

if (-not (Test-Path -LiteralPath $envFile)) {
  throw 'Missing local .env file. Copy .env.example and add the required environment variables.'
}

foreach ($line in Get-Content -LiteralPath $envFile) {
  $trimmedLine = $line.Trim()
  if ([string]::IsNullOrWhiteSpace($trimmedLine) -or $trimmedLine.StartsWith('#')) {
    continue
  }

  $separatorIndex = $trimmedLine.IndexOf('=')
  if ($separatorIndex -lt 1) {
    throw 'Invalid .env entry. Use NAME=value format.'
  }

  $name = $trimmedLine.Substring(0, $separatorIndex).Trim()
  $value = $trimmedLine.Substring($separatorIndex + 1).Trim()
  if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
    throw 'Invalid environment variable name in .env.'
  }

  if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
    $value = $value.Substring(1, $value.Length - 2)
  }

  [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

foreach ($name in $requiredVariables) {
  if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
    throw "Missing required environment variable: $name"
  }
}

if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
  throw 'JAVA_HOME must be provided by the VS Code launch configuration.'
}

& (Join-Path $projectRoot 'gradlew.bat') bootRun
exit $LASTEXITCODE
