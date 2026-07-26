<#
.SYNOPSIS
    Points Supabase Auth at the Kosht mailbox and uploads the code email.

.DESCRIPTION
    Sign-up and password-reset codes are sent by Supabase, not by the app —
    an SMTP password shipped inside an APK is a password given away. Which
    mailbox Supabase uses is project configuration, and this script writes it
    from the repository so nothing has to be retyped into a dashboard:

      * SMTP host, port, user, password, sender address and sender name,
        all read from .env (KOSHT_SMTP_*)
      * the confirmation, magic-link and recovery templates, all three set to
        docs/email/confirm-code.html, because a resend must not fall back to
        Supabase's own wording
      * the subject line, and a code lifetime of 300 seconds to match the
        five minutes the app counts down on screen

    Everything it sends is idempotent: run it again after editing the template
    or rotating the app password.

.PARAMETER Token
    A Supabase personal access token (starts with sbp_), from
    https://supabase.com/dashboard/account/tokens — this is the one thing that
    cannot live in the repository. Defaults to $env:SUPABASE_ACCESS_TOKEN or a
    SUPABASE_ACCESS_TOKEN entry in .env.

.PARAMETER WhatIf
    Print what would be sent, without sending it.

.EXAMPLE
    .\scripts\supabase-mailer.ps1 -Token sbp_xxx
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$Token
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# --- .env -------------------------------------------------------------------

$envPath = Join-Path $root '.env'
if (-not (Test-Path $envPath)) { throw ".env not found at $envPath" }

$settings = @{}
foreach ($line in Get-Content $envPath) {
    $trimmed = $line.Trim()
    if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
    $at = $trimmed.IndexOf('=')
    if ($at -lt 1) { continue }
    $settings[$trimmed.Substring(0, $at).Trim()] = $trimmed.Substring($at + 1).Trim().Trim('"')
}

function Need($key) {
    if (-not $settings.ContainsKey($key) -or $settings[$key] -eq '') {
        throw "$key is missing from .env"
    }
    return $settings[$key]
}

if (-not $Token) { $Token = $env:SUPABASE_ACCESS_TOKEN }
if (-not $Token -and $settings.ContainsKey('SUPABASE_ACCESS_TOKEN')) {
    $Token = $settings['SUPABASE_ACCESS_TOKEN']
}
if (-not $Token) {
    throw "No access token. Pass -Token sbp_..., or add SUPABASE_ACCESS_TOKEN to .env. Create one at https://supabase.com/dashboard/account/tokens"
}

# The project ref is the first label of the Supabase URL.
$projectRef = ([Uri](Need 'SUPABASE_URL')).Host.Split('.')[0]

# --- the email itself -------------------------------------------------------

$templatePath = Join-Path $root 'docs\email\confirm-code.html'
$template = Get-Content $templatePath -Raw -Encoding UTF8
# The note at the top of the file explains the template to whoever edits it;
# it has no business travelling to the recipient.
$template = [regex]::Replace($template, '(?s)^\s*<!--.*?-->\s*', '')
if ($template -notmatch '\{\{\s*\.Token\s*\}\}') {
    throw "$templatePath has no {{ .Token }} placeholder — the code would never reach the reader"
}

$subject = 'Kosht · код подтверждения {{ .Token }}'

$config = [ordered]@{
    smtp_host        = Need 'KOSHT_SMTP_HOST'
    smtp_port        = Need 'KOSHT_SMTP_PORT'
    smtp_user        = Need 'KOSHT_SMTP_USER'
    smtp_pass        = Need 'KOSHT_SMTP_PASSWORD'
    # Gmail refuses to send as anyone but the mailbox itself.
    smtp_admin_email = Need 'KOSHT_SMTP_SENDER'
    smtp_sender_name = Need 'KOSHT_SMTP_SENDER_NAME'
    # One code a minute per address; the app offers "resend" after the same wait.
    smtp_max_frequency = 60

    # The app counts five minutes down on screen, but the server is the
    # authority — its default is an hour.
    mailer_otp_exp   = 300
    # The code field in the app is six digits wide, so the code has to be six.
    mailer_otp_length = 6

    mailer_subjects_confirmation = $subject
    mailer_subjects_magic_link   = $subject
    mailer_subjects_recovery     = $subject
    mailer_templates_confirmation_content = $template
    mailer_templates_magic_link_content   = $template
    mailer_templates_recovery_content     = $template
}

$endpoint = "https://api.supabase.com/v1/projects/$projectRef/config/auth"

Write-Host "Project      : $projectRef"
Write-Host "Mailbox      : $($config.smtp_user) via $($config.smtp_host):$($config.smtp_port)"
Write-Host "Sender name  : $($config.smtp_sender_name)"
Write-Host "Template     : $templatePath ($($template.Length) chars)"
Write-Host "Code lifetime: $($config.mailer_otp_exp) s"

if (-not $PSCmdlet.ShouldProcess($endpoint, 'PATCH auth config')) { return }

$body = $config | ConvertTo-Json -Depth 4
$headers = @{ Authorization = "Bearer $Token"; 'Content-Type' = 'application/json' }

try {
    $response = Invoke-RestMethod -Method Patch -Uri $endpoint -Headers $headers -Body $body
    Write-Host ''
    Write-Host 'Applied. Supabase now sends from:' -ForegroundColor Green
    Write-Host "  $($response.smtp_sender_name) <$($response.smtp_admin_email)>"
    Write-Host "  code lifetime $($response.mailer_otp_exp) s, one code per $($response.smtp_max_frequency) s"
    Write-Host ''
    Write-Host 'Send yourself a code from the app to confirm the From: line.'
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Host "Failed with HTTP $status" -ForegroundColor Red
    if ($status -eq 401 -or $status -eq 403) {
        Write-Host 'The token is rejected. It must be a personal access token (sbp_...) from'
        Write-Host 'https://supabase.com/dashboard/account/tokens — the service-role key and the'
        Write-Host 'anon key do not work on the Management API.'
    }
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
    throw
}
