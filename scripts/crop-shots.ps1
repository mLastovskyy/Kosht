# Crops the interesting part out of a full 1080x2400 emulator screenshot.
#
# The manual shows a region rather than a whole screen: a dialog or a block of
# rows reads far better in a document than a phone with three quarters of empty
# background. Coordinates are in the screenshot's own pixels.
#
# Usage: .\scripts\crop-shots.ps1 -Source <in.png> -Target <out.png> -Top 300 -Bottom 1700 [-Left 0] [-Right 1080]
param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Target,
    [Parameter(Mandatory = $true)][int]$Top,
    [Parameter(Mandatory = $true)][int]$Bottom,
    [int]$Left = 0,
    [int]$Right = 1080
)

Add-Type -AssemblyName System.Drawing

$image = [System.Drawing.Image]::FromFile((Resolve-Path $Source))
try {
    $width = [Math]::Min($Right, $image.Width) - $Left
    $height = [Math]::Min($Bottom, $image.Height) - $Top
    $rect = New-Object System.Drawing.Rectangle($Left, $Top, $width, $height)
    $crop = New-Object System.Drawing.Bitmap($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($crop)
    try {
        $graphics.DrawImage($image, (New-Object System.Drawing.Rectangle(0, 0, $width, $height)), $rect, [System.Drawing.GraphicsUnit]::Pixel)
    } finally {
        $graphics.Dispose()
    }
    $directory = Split-Path -Parent $Target
    if ($directory -and -not (Test-Path $directory)) {
        New-Item -ItemType Directory -Force $directory | Out-Null
    }
    $crop.Save($Target, [System.Drawing.Imaging.ImageFormat]::Png)
    $crop.Dispose()
    "$Target : ${width}x${height}"
} finally {
    $image.Dispose()
}
