param(
    [Parameter(Mandatory = $true)]
    [string] $ApkPath,

    [Parameter(Mandatory = $true)]
    [string] $ZipAlignPath
)

$ErrorActionPreference = 'Stop'
$pageSize = [UInt64]16384
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
$zipAlign = (Resolve-Path -LiteralPath $ZipAlignPath).Path

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($apk)
$libraryCount = 0
$failures = [System.Collections.Generic.List[string]]::new()

try {
    foreach ($entry in $archive.Entries | Where-Object { $_.FullName -match '^lib/[^/]+/[^/]+\.so$' }) {
        $libraryCount++
        $stream = $entry.Open()
        $memory = [System.IO.MemoryStream]::new()
        try {
            $stream.CopyTo($memory)
            $bytes = $memory.ToArray()
        }
        finally {
            $memory.Dispose()
            $stream.Dispose()
        }

        if ($bytes.Length -lt 52 -or $bytes[0] -ne 0x7f -or $bytes[1] -ne 0x45 -or
                $bytes[2] -ne 0x4c -or $bytes[3] -ne 0x46) {
            $failures.Add("$($entry.FullName): invalid or truncated ELF")
            continue
        }
        if ($bytes[5] -ne 1) {
            $failures.Add("$($entry.FullName): only little-endian ELF is supported by this checker")
            continue
        }

        $elfClass = $bytes[4]
        if ($elfClass -eq 2 -and $bytes.Length -ge 64) {
            $programHeaderOffset = [BitConverter]::ToUInt64($bytes, 32)
            $programHeaderSize = [BitConverter]::ToUInt16($bytes, 54)
            $programHeaderCount = [BitConverter]::ToUInt16($bytes, 56)
        }
        elseif ($elfClass -eq 1) {
            $programHeaderOffset = [UInt64][BitConverter]::ToUInt32($bytes, 28)
            $programHeaderSize = [BitConverter]::ToUInt16($bytes, 42)
            $programHeaderCount = [BitConverter]::ToUInt16($bytes, 44)
        }
        else {
            $failures.Add("$($entry.FullName): unsupported ELF class $elfClass")
            continue
        }

        if ($programHeaderSize -eq 0 -or
                $programHeaderOffset + ([UInt64]$programHeaderSize * $programHeaderCount) -gt $bytes.Length) {
            $failures.Add("$($entry.FullName): invalid ELF program header table")
            continue
        }

        $loadCount = 0
        $alignments = [System.Collections.Generic.List[string]]::new()
        for ($index = 0; $index -lt $programHeaderCount; $index++) {
            $header = [int]($programHeaderOffset + ([UInt64]$index * $programHeaderSize))
            if ([BitConverter]::ToUInt32($bytes, $header) -ne 1) {
                continue
            }

            $loadCount++
            if ($elfClass -eq 2) {
                $offset = [BitConverter]::ToUInt64($bytes, $header + 8)
                $virtualAddress = [BitConverter]::ToUInt64($bytes, $header + 16)
                $alignment = [BitConverter]::ToUInt64($bytes, $header + 48)
            }
            else {
                $offset = [UInt64][BitConverter]::ToUInt32($bytes, $header + 4)
                $virtualAddress = [UInt64][BitConverter]::ToUInt32($bytes, $header + 8)
                $alignment = [UInt64][BitConverter]::ToUInt32($bytes, $header + 28)
            }

            $alignments.Add(('0x{0:X}' -f $alignment))
            if ($alignment -lt $pageSize -or $alignment -eq 0 -or
                    ($offset % $alignment) -ne ($virtualAddress % $alignment)) {
                $failures.Add(('{0}: PT_LOAD #{1} has p_align=0x{2:X}, file offset=0x{3:X}, virtual address=0x{4:X}' -f
                        $entry.FullName, $loadCount, $alignment, $offset, $virtualAddress))
            }
        }

        if ($loadCount -eq 0) {
            $failures.Add("$($entry.FullName): ELF contains no PT_LOAD segments")
        }
        Write-Output ('ELF {0}: PT_LOAD={1}; p_align={2}' -f
                $entry.FullName, $loadCount, ($alignments -join ','))
    }
}
finally {
    $archive.Dispose()
}

if ($libraryCount -eq 0) {
    throw "No native shared libraries found in APK: $apk"
}

Write-Output "Checking APK ZIP alignment with: $zipAlign"
& $zipAlign -c -P 16 4 $apk
if ($LASTEXITCODE -ne 0) {
    $failures.Add("zipalign -c -P 16 4 failed with exit code $LASTEXITCODE")
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ -ErrorAction Continue }
    throw "16 KB compatibility check failed for $($failures.Count) condition(s)"
}

Write-Output "16 KB ELF and APK ZIP alignment verified for $libraryCount native libraries."
