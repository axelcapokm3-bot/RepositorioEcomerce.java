# Escanea solo archivos trackeados por git en busca de credenciales.
# No imprime los valores encontrados: solo ruta, linea y tipo de hallazgo.
# Salida: 0 sin hallazgos, 1 con hallazgos, 2 error de entorno.

$ErrorActionPreference = 'Stop'

$repo = & git rev-parse --show-toplevel 2>$null
if (-not $? -or [string]::IsNullOrWhiteSpace($repo)) {
    Write-Error 'No se pudo determinar la raiz del repositorio git.'
    exit 2
}

$patrones = @(
    @{ Nombre = 'Stripe secret key'; Patron = '\bsk_(live|test)_[0-9A-Za-z]{16,}' },
    @{ Nombre = 'Stripe restricted key'; Patron = '\brk_(live|test)_[0-9A-Za-z]{16,}' },
    @{ Nombre = 'Stripe publishable key live'; Patron = '\bpk_live_[0-9A-Za-z]{16,}' },
    @{ Nombre = 'Mercado Pago access token'; Patron = '\bAPP_USR-[0-9A-Za-z-]{10,}' },
    @{ Nombre = 'AWS access key'; Patron = '\bAKIA[0-9A-Z]{16}\b' },
    @{ Nombre = 'Clave privada PEM'; Patron = '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----' },
    @{
        Nombre = 'Credencial literal'
        Patron = '(?i)\b(password|passwd|contrasena|contrase\u00f1a|db\.password|api[_-]?secret)\b\s*[=:]\s*["'']([^"'']{8,})["'']'
        Extensiones = $null
    },
    @{
        Nombre = 'Credencial en archivo de configuracion'
        Patron = '(?i)^\s*[A-Za-z0-9_.-]*(password|passwd|contrasena|contrase\u00f1a|secret|token)[A-Za-z0-9_.-]*\s*[=:]\s*([^\s#]{8,})'
        Extensiones = @('.properties', '.env', '.yml', '.yaml', '.ini', '.conf', '.cnf')
    }
)

$marcadoresFalsos = 'REEMPLAZAR|CAMBIAR|CHANGEME|YOUR_|PLACEHOLDER|EXAMPLE|xxxx|\*\*\*|<[^>]+>|\$\{'

$exclusiones = '(^|/)(\.git|target|node_modules|bin|build|dist|out|coverage)(/|$)|\.(jar|war|ear|class|zip|tar|gz|7z|png|jpe?g|gif|ico|pdf|woff2?|ttf|eot|mp4|mp3|wav|dll|exe|so|dylib|lock)$'
$archivos = (& git -C $repo ls-files -z) -split "`0" | Where-Object {
    -not [string]::IsNullOrWhiteSpace($_) -and $_ -notmatch $exclusiones
}
$hallazgos = 0

foreach ($relativo in $archivos) {
    if ($relativo -match $exclusiones) {
        continue
    }
    $ruta = Join-Path $repo $relativo
    if (-not (Test-Path -LiteralPath $ruta -PathType Leaf)) {
        continue
    }
    if ((Get-Item -LiteralPath $ruta).Length -gt 1MB) {
        continue
    }

    try {
        $lineas = Get-Content -LiteralPath $ruta -Encoding UTF8 -ErrorAction Stop
    } catch {
        continue
    }

    for ($indice = 0; $indice -lt $lineas.Count; $indice++) {
        $linea = [string]$lineas[$indice]
        foreach ($patron in $patrones) {
            if ($patron.Extensiones) {
                $extension = [System.IO.Path]::GetExtension($relativo).ToLowerInvariant()
                $nombreArchivo = [System.IO.Path]::GetFileName($relativo).ToLowerInvariant()
                if ($extension -notin $patron.Extensiones -and $nombreArchivo -ne '.env') {
                    continue
                }
            }
            if ($linea -notmatch $patron.Patron) {
                continue
            }
            if ($linea -match $marcadoresFalsos) {
                continue
            }
            Write-Output ("{0}:{1}: {2}" -f $relativo, ($indice + 1), $patron.Nombre)
            $hallazgos++
            break
        }
    }
}

if ($hallazgos -gt 0) {
    Write-Warning ("Se detectaron {0} posibles secretos en archivos trackeados." -f $hallazgos)
    exit 1
}

Write-Output ("Sin secretos detectados en {0} archivos trackeados." -f $archivos.Count)
exit 0
