$ErrorActionPreference = "Stop"
try {
    $loginPayload = @{ email = "test@test.com"; password = "123456" } | ConvertTo-Json
    $loginResponse = Invoke-RestMethod -Uri "http://127.0.0.1:18080/api/auth/login" -Method Post -ContentType "application/json" -Body $loginPayload
    $token = $loginResponse.token
    $headers = @{ 
        Authorization = "Bearer $token"
        "Content-Type" = "application/json"
    }
    Write-Host "1. Login Success"

    $diaryPayload = @{ content = "长期画像验收词A1B2C3。我现在的长期目标是转行产品经理。和妈妈沟通紧张。我希望你先倾听再建议。" } | ConvertTo-Json
    $diaryResponse = Invoke-RestMethod -Uri "http://127.0.0.1:18080/api/diaries" -Method Post -Headers $headers -Body $diaryPayload
    $diaryId = $diaryResponse.id
    Write-Host "2. Diary Created ID: $diaryId"

    $analysis = ""
    for ($i = 1; $i -le 15; $i++) {
        Start-Sleep -Seconds 2
        $diaryDetail = Invoke-RestMethod -Uri "http://127.0.0.1:18080/api/diaries/$diaryId" -Method Get -Headers $headers
        if ($diaryDetail.analysis) {
            $analysis = $diaryDetail.analysis
            break
        }
        Write-Host "3. Polling... ($i)"
    }
    Write-Host "Analysis Result: $analysis"

    $convResponse = Invoke-RestMethod -Uri "http://127.0.0.1:18080/api/chat/conversations" -Method Post -Headers $headers
    $convId = $convResponse.id

    $r1 = Invoke-RestMethod -Uri "http://127.0.0.1:18080/api/chat/conversations/$convId/reply" -Method Post -Headers $headers -Body (@{ content = "你记得我的长期目标和我希望你怎么回应吗？请简短说。" } | ConvertTo-Json)
    Write-Host "Reply 1: $($r1.content)"

    $r2 = Invoke-RestMethod -Uri "http://127.0.0.1:18080/api/chat/conversations/$convId/reply" -Method Post -Headers $headers -Body (@{ content = "请帮我查一下包含‘长期画像验收词A1B2C3’这条历史日记的摘要，并告诉我日期。只基于历史记录回答。" } | ConvertTo-Json)
    Write-Host "Reply 2: $($r2.content)"

    $r3 = Invoke-RestMethod -Uri "http://127.0.0.1:18080/api/chat/conversations/$convId/reply" -Method Post -Headers $headers -Body (@{ content = "请检索今天到今天的历史日记摘要，给我最多3条，按时间倒序。" } | ConvertTo-Json)
    Write-Host "Reply 3: $($r3.content)"
} catch {
    Write-Host "FAILED"
    Write-Host $_.Exception.Message
}
