$ErrorActionPreference = "Stop"
$baseUrl = "http://127.0.0.1:18080/api"

try {
    Write-Host "--- 1. Login ---"
    $body = @{ email = "test@test.com"; password = "123456" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType "application/json" -Body $body
    $token = $res.data.token
    $hdrs = @{ "Authorization" = "Bearer $token"; "Content-Type" = "application/json" }
    Write-Host "Success"

    Write-Host "`n--- 2. Create Diary ---"
    $dBody = @{ content = "长期画像验收词A1B2C3。我最近持续想转行产品经理，和妈妈沟通有些紧张。我更希望先被倾听，再收到建议。"; visibility = "PRIVATE" } | ConvertTo-Json
    $dRes = Invoke-RestMethod -Uri "$baseUrl/diaries" -Method Post -Headers $hdrs -Body $dBody
    $id = $dRes.data.id
    Write-Host "ID: $id"

    Write-Host "`n--- 3. Polling ---"
    $ana = $null
    for ($i=1; $i -le 15; $i++) {
        Start-Sleep -s 3
        $c = Invoke-RestMethod -Uri "$baseUrl/diaries/$id" -Method Get -Headers $hdrs
        if ($c.data.analysis) { $ana = $c.data.analysis; break }
        Write-Host "Waiting $i..."
    }
    Write-Host "Mood: $($ana.moodLabel)"

    Write-Host "`n--- 4. Conversation ---"
    $vRes = Invoke-WebRequest -Uri "$baseUrl/chat/conversations" -Method Post -Headers $hdrs -Body "{}"
    Write-Host "Create Conv Status: $($vRes.StatusCode)"
    $vData = $vRes.Content | ConvertFrom-Json
    $vid = $vData.data.id
    Write-Host "Conv ID: $vid"
    
    $qs = @(
        "你记得我的长期目标和我希望你怎么回应吗？",
        "请帮我查一下包含 A1B2C3 这条历史日记的摘要。",
        "检索今天的历史日记摘要。"
    )
    $results = @()
    foreach ($q in $qs) {
        Write-Host "`nQuery: $q"
        try {
            $r = Invoke-RestMethod -Uri "$baseUrl/chat/conversations/$vid/reply" -Method Post -Headers $hdrs -Body (@{message=$q}|ConvertTo-Json)
            $txt = $r.data.reply
            Write-Host "Reply: $txt"
            $results += $txt
        } catch {
            Write-Host "Reply Failed."
            if ($_.Exception.Response) {
                $stream = $_.Exception.Response.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                Write-Host "Error Body: $($reader.ReadToEnd())"
            }
            throw $_
        }
    }

    Write-Host "`n--- FINAL CONCLUSION ---"
    $a_status = "不通过"; if ($results[0] -match "产品经理" -or $results[0] -match "倾听") { $a_status = "通过" }
    $b_status = "不通过"; if ($results[1] -match "A1B2C3" -or $results[1] -match "产品经理") { $b_status = "通过" }
    Write-Host "A. 长期画像: $a_status (证据: $($results[0]))"
    Write-Host "B. 历史检索: $b_status (证据: $($results[1]))"
} catch {
    Write-Host "Error: $_"
}
