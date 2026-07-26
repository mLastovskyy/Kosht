# Renders slips the way a phone sees them — skewed, unevenly lit, softened and
# saved as a middling JPEG — so the reader can be measured on something other
# than a perfect screenshot. Output goes to the folder given (default: build).
#
#   powershell -File scripts\make-receipt-photos.ps1 -OutDir bench
#
# Every slip carries its own answer in <name>.total so the bench can score itself.

param([string]$OutDir = "build\receipt-photos")

Add-Type -AssemblyName System.Drawing

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$slips = @(
    @{
        name  = "euroopt"
        total = "1181"
        lines = @(
            'КАССОВЫЙ ЧЕК', 'ООО "Евроопт"', 'УНП 190239501',
            'г. Минск, ул. Притыцкого, 29', 'Магазин №312   Смена 4',
            'Кассир: Иванова М.П.', '26.07.2026 19:42', '',
            'Молоко Савушкин 3,2% 1л      2,45',
            'Хлеб Нарочанский             1,89',
            'Сыр Тильзитер 45% 200г       7,30',
            'Пакет майка                  0,17', '',
            'ИТОГО К ОПЛАТЕ:             11,81',
            'НДС 20%                      1,97',
            'ОПЛАТА КАРТОЙ               11,81',
            'БЕЛКАРТ **** 4417', 'СПАСИБО ЗА ПОКУПКУ!'
        )
    },
    @{
        name  = "cafe"
        total = "1187"
        lines = @(
            'Кофейня на Немиге', 'ЧТУП "Прима Тэйст", УНП 191884203',
            'ул. Немига, 5, Минск', 'Заказ №118    26.07.2026 09:14', '',
            'Cappuccino 300ml             6,50',
            'Круассан с миндалём          4,80',
            'Обслуживание 5%              0,57', '',
            'ИТОГО                       11,87',
            'Оплата: карта', 'Ждём вас снова!'
        )
    },
    @{
        # Nothing on this one says "итого" — the line model has to find the sum.
        name  = "no-keyword"
        total = "992"
        lines = @(
            'ООО "Виталюр"', 'УНП 190239501', 'г. Минск, ул. Кульман, 1',
            'Кассир: Петров А.С.', '26.07.2026 18:02', '',
            'Молоко Савушкин 3,2% 1л      2,45',
            'Сыр Тильзитер 45% 200г       7,30',
            'Пакет майка                  0,17', '',
            'ОПЛАЧЕНО КАРТОЙ              9,92',
            'НДС 20%                      1,65', 'СПАСИБО ЗА ПОКУПКУ'
        )
    }
)

function New-Slip($slip, $index) {
    $width = 900
    $height = 120 + $slip.lines.Count * 46
    $paper = New-Object System.Drawing.Bitmap($width, $height)
    $g = [System.Drawing.Graphics]::FromImage($paper)
    $g.Clear([System.Drawing.Color]::White)
    $g.TextRenderingHint = 'AntiAliasGridFit'
    $font = New-Object System.Drawing.Font("Consolas", 26, [System.Drawing.FontStyle]::Regular)
    $bold = New-Object System.Drawing.Font("Consolas", 30, [System.Drawing.FontStyle]::Bold)
    $ink = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(28, 28, 28))

    $y = 50
    foreach ($line in $slip.lines) {
        $use = if ($line -match '^(КАССОВЫЙ|ООО|ЧТУП|Кофейня)') { $bold } else { $font }
        $g.DrawString($line, $use, $ink, 40, $y)
        $y += 46
    }
    $g.Dispose()

    # A photograph of that paper: turned a little, lit from one side, soft, JPEG.
    $shot = New-Object System.Drawing.Bitmap(($width + 200), ($height + 200))
    $h = [System.Drawing.Graphics]::FromImage($shot)
    $h.Clear([System.Drawing.Color]::FromArgb(210, 212, 208))
    $h.InterpolationMode = 'HighQualityBicubic'
    $h.TranslateTransform(($width + 200) / 2, ($height + 200) / 2)
    $h.RotateTransform(-2.5 + $index * 2.0)
    $h.TranslateTransform(-$width / 2, -$height / 2)
    $h.DrawImage($paper, 0, 0, $width, $height)
    $h.ResetTransform()

    $shade = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Point(0, 0)),
        (New-Object System.Drawing.Point($shot.Width, $shot.Height)),
        [System.Drawing.Color]::FromArgb(90, 0, 0, 0),
        [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
    $h.FillRectangle($shade, 0, 0, $shot.Width, $shot.Height)
    $h.Dispose()

    # Softening: down and back up again, which is what a shaky hand does to detail.
    $small = New-Object System.Drawing.Bitmap($shot, [System.Drawing.Size]::new([int]($shot.Width * 0.62), [int]($shot.Height * 0.62)))
    $soft = New-Object System.Drawing.Bitmap($shot.Width, $shot.Height)
    $s = [System.Drawing.Graphics]::FromImage($soft)
    $s.InterpolationMode = 'HighQualityBilinear'
    $s.DrawImage($small, 0, 0, $soft.Width, $soft.Height)
    $s.Dispose()

    $codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
        Where-Object { $_.MimeType -eq 'image/jpeg' }
    $params = New-Object System.Drawing.Imaging.EncoderParameters(1)
    $params.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter(
        [System.Drawing.Imaging.Encoder]::Quality, 62)
    $path = Join-Path $OutDir ($slip.name + ".jpg")
    $soft.Save($path, $codec, $params)
    Set-Content -Path (Join-Path $OutDir ($slip.name + ".total")) -Value $slip.total -NoNewline

    $paper.Dispose(); $shot.Dispose(); $small.Dispose(); $soft.Dispose()
    "{0}  {1}x{2}" -f $path, $soft.Width, $soft.Height
}

$i = 0
foreach ($slip in $slips) { New-Slip $slip $i; $i++ }
