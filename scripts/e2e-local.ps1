# ChainLance End-to-End Local Verification Script (PowerShell)
# Exercises: Auth -> Projects -> Proposals -> Offers -> Agreement Snapshot ->
#            Wallets -> Milestone Generation & Remainder Invariance -> Extensions -> Settlements

$ErrorActionPreference = "Stop"

function Write-Step([string]$message) {
    Write-Host "`n=== $message ===" -ForegroundColor Cyan
}

function Write-Pass([string]$message) {
    Write-Host "  [PASS] $message" -ForegroundColor Green
}

function Write-Fail([string]$message) {
    Write-Host "  [FAIL] $message" -ForegroundColor Red
    exit 1
}

$baseUrl = "http://localhost:8080"
$api = "$baseUrl/api"

# 1. Health Check
Write-Step "1. Checking Backend Health"
try {
    $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method Get
    if ($health.status -ne "UP") {
        Write-Fail "Backend reported status: $($health.status)"
    }
    Write-Pass "Backend is UP at $baseUrl"
} catch {
    Write-Fail "Backend is not reachable at $baseUrl. Start it first using backend\run-local.ps1"
}

# Generate unique timestamps
$ts = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$clientEmail = "client_$ts@chainlance.local"
$freeEmail = "free_$ts@chainlance.local"
$pass = "P@ssword1234!"

# 2. Register Client
Write-Step "2. Registering Client ($clientEmail)"
$clientReg = @{
    email = $clientEmail
    password = $pass
    displayName = "E2E Test Client"
    role = "CLIENT"
} | ConvertTo-Json
$clientAuth = Invoke-RestMethod -Uri "$api/auth/register" -Method Post -Body $clientReg -ContentType "application/json"
$clientToken = $clientAuth.token
if (-not $clientToken) { Write-Fail "Failed to register client" }
Write-Pass "Client registered successfully (ID: $($clientAuth.user.id))"

# 3. Register Freelancer
Write-Step "3. Registering Freelancer ($freeEmail)"
$freeReg = @{
    email = $freeEmail
    password = $pass
    displayName = "E2E Test Freelancer"
    role = "FREELANCER"
} | ConvertTo-Json
$freeAuth = Invoke-RestMethod -Uri "$api/auth/register" -Method Post -Body $freeReg -ContentType "application/json"
$freeToken = $freeAuth.token
if (-not $freeToken) { Write-Fail "Failed to register freelancer" }
Write-Pass "Freelancer registered successfully (ID: $($freeAuth.user.id))"

$clientHeaders = @{ Authorization = "Bearer $clientToken" }
$freeHeaders = @{ Authorization = "Bearer $freeToken" }

# 4. Check Chain Config
Write-Step "4. Reading Chain Configuration"
$chainConfig = Invoke-RestMethod -Uri "$api/chain/config" -Method Get -Headers $clientHeaders
Write-Pass "Chain ID: $($chainConfig.chainId), Pricing Rate: $($chainConfig.inrPerEth) INR/ETH"

# 5. Client Creates and Publishes Project
Write-Step "5. Creating and Publishing Project"
$futureDeadline = [DateTime]::UtcNow.AddDays(30).ToString("yyyy-MM-ddTHH:mm:ssZ")
$projBody = @{
    title = "E2E Smart Contract Integration Project"
    description = "Full escrow development project for end-to-end testing."
    category = "Blockchain"
    skills = "Solidity, Java, React, TypeScript"
    budgetInr = 50000.00
    deadline = $futureDeadline
} | ConvertTo-Json
$project = Invoke-RestMethod -Uri "$api/projects" -Method Post -Headers $clientHeaders -Body $projBody -ContentType "application/json"
$projectId = $project.id
Write-Pass "Project created with ID $projectId in DRAFT status"

# Save a 3-milestone plan (30%, 40%, 30%)
$planBody = @(
    @{ title = "Design & Spec"; description = "Architecture spec"; percentage = 30 }
    @{ title = "Implementation"; description = "Core smart contracts"; percentage = 40 }
    @{ title = "Audits & Delivery"; description = "Testing and verification"; percentage = 30 }
) | ConvertTo-Json
$plan = Invoke-RestMethod -Uri "$api/projects/$projectId/milestone-plan" -Method Put -Headers $clientHeaders -Body $planBody -ContentType "application/json"
Write-Pass "Project milestone plan configured (3 items, 100% total share)"

# Publish project
$pub = Invoke-RestMethod -Uri "$api/projects/$projectId/publish" -Method Post -Headers $clientHeaders
if ($pub.status -ne "OPEN") { Write-Fail "Expected project OPEN but got $($pub.status)" }
Write-Pass "Project published to marketplace (status: OPEN)"

# 6. Freelancer Submits Proposal
Write-Step "6. Submitting Proposal"
$propBody = @{
    message = "I am an experienced Solidity engineer. Happy to deliver your escrow project."
    priceInr = 50000.00
    durationDays = 21
    portfolioLinks = "https://github.com/chainlance"
} | ConvertTo-Json
$proposal = Invoke-RestMethod -Uri "$api/projects/$projectId/proposals" -Method Post -Headers $freeHeaders -Body $propBody -ContentType "application/json"
$proposalId = $proposal.id
Write-Pass "Proposal submitted with ID $proposalId at 50,000 INR"

# 7. Client Counters with Offer
Write-Step "7. Negotiating Proposal Offer"
$firstOfferId = $proposal.offers[0].id
$offerBody = @{
    priceInr = 45000.00
    durationDays = 14
    note = "Can we do 45,000 INR within 14 days?"
} | ConvertTo-Json
$counter = Invoke-RestMethod -Uri "$api/proposals/$proposalId/offers/$firstOfferId/counter" -Method Post -Headers $clientHeaders -Body $offerBody -ContentType "application/json"
$counterOfferId = $counter.id
Write-Pass "Counter-offer created with ID $counterOfferId (45,000 INR, 14 days)"

# Freelancer accepts counter-offer
$accOffer = Invoke-RestMethod -Uri "$api/proposals/$proposalId/offers/$counterOfferId/accept" -Method Post -Headers $freeHeaders
Write-Pass "Offer accepted by freelancer (status: $($accOffer.status))"

# 8. Client Accepts Proposal -> Agreement Created
Write-Step "8. Finalizing Proposal Acceptance & Agreement"
$agreement = Invoke-RestMethod -Uri "$api/proposals/$proposalId/accept" -Method Post -Headers $clientHeaders
$agreementId = $agreement.id

if (-not $agreement.snapshot) { Write-Fail "Agreement snapshot missing" }
if (-not $agreement.snapshotHash) { Write-Fail "Agreement snapshot hash missing" }
if (-not $agreement.snapshotIntegrityOk) { Write-Fail "Agreement snapshot integrity check failed" }

# Total wei check: 45000 / 250000 = 0.18 ETH = 180000000000000000 wei
$expectedWei = "180000000000000000"
if ($agreement.totalWei -ne $expectedWei) {
    Write-Fail "Expected totalWei $expectedWei but got $($agreement.totalWei)"
}
Write-Pass "Agreement #$agreementId created (status: $($agreement.status))"
Write-Pass "Snapshot Hash: $($agreement.snapshotHash) (Integrity: OK)"
Write-Pass "Indicative Price: $($agreement.priceInr) INR = $($agreement.totalWei) wei (0.18 ETH)"

# Helper function: verifies ownership of a wallet via challenge + personal_sign and links it
function Verify-And-Link-Wallet($token, $agId, $label) {
    $helperScript = Join-Path $PSScriptRoot "sign-challenge.cjs"
    $w = node $helperScript create | ConvertFrom-Json
    $addr = $w.address
    $privKey = $w.privateKey

    # Challenge
    $chReq = @{ address = $addr } | ConvertTo-Json
    $chResp = Invoke-RestMethod -Uri "$api/wallet/challenge" -Method Post -Headers @{ Authorization = "Bearer $token" } -Body $chReq -ContentType "application/json"

    # Sign challenge message with private key
    $sigObj = node $helperScript sign $privKey $chResp.message | ConvertFrom-Json

    # Verify challenge response
    $verReq = @{ nonce = $chResp.nonce; signature = $sigObj.signature } | ConvertTo-Json
    Invoke-RestMethod -Uri "$api/wallet/verify" -Method Post -Headers @{ Authorization = "Bearer $token" } -Body $verReq -ContentType "application/json" | Out-Null

    # Link verified wallet to agreement
    $linkReq = @{ address = $addr } | ConvertTo-Json
    $updated = Invoke-RestMethod -Uri "$api/agreements/$agId/wallets" -Method Post -Headers @{ Authorization = "Bearer $token" } -Body $linkReq -ContentType "application/json"
    Write-Pass "$label verified & linked wallet: $addr"
    return $addr
}

# 9. Linking Wallets
Write-Step "9. Linking Wallets to Agreement"
$clientWallet = Verify-And-Link-Wallet $clientToken $agreementId "Client"
$freeWallet = Verify-And-Link-Wallet $freeToken $agreementId "Freelancer"

# 10. Generate Milestones
Write-Step "10. Generating Milestones"
$milestones = Invoke-RestMethod -Uri "$api/agreements/$agreementId/milestones/generate" -Method Post -Headers $clientHeaders
if ($milestones.Count -ne 3) { Write-Fail "Expected 3 generated milestones, got $($milestones.Count)" }

[decimal]$sumInr = 0
[System.Numerics.BigInteger]$sumWei = [System.Numerics.BigInteger]::Zero

foreach ($m in $milestones) {
    $sumInr += [decimal]$m.amountInr
    $sumWei += [System.Numerics.BigInteger]::Parse($m.amountWei)
    Write-Host "   Milestone $($m.position): '$($m.title)' -> $($m.amountInr) INR ($($m.amountWei) wei)" -ForegroundColor Gray
}

$expectedSumWei = [System.Numerics.BigInteger]::Parse($expectedWei)
if ($sumInr -ne 45000.00) { Write-Fail "Sum of milestone INR ($sumInr) != 45000.00" }
if ($sumWei -ne $expectedSumWei) { Write-Fail "Sum of milestone wei ($sumWei) != $expectedSumWei" }
Write-Pass "Milestone scaling verified: 100% of INR and wei accounted for with zero remainder loss."

# 11. End-to-End Workflow & Chain Verification (Submissions, Extensions, Settlements, Disputes, Reviews)
Write-Step "11. Verifying Full Escrow & Post-Funding Workflow"
Write-Host "Running comprehensive workflow test suite (WorkflowFlowMySqlTest)..." -ForegroundColor Gray

$backendDir = Join-Path $PSScriptRoot "..\backend"
$env:TEST_DB_URL = "jdbc:mysql://localhost:3306/chainlance_test?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"
$env:TEST_DB_USERNAME = "chainlance"
$env:TEST_DB_PASSWORD = "saisagar"

Push-Location $backendDir
try {
    $mvnOut = mvn test -Dtest=WorkflowFlowMySqlTest
    $failed = $LASTEXITCODE -ne 0
    if ($failed) {
        Write-Fail "Workflow test execution failed"
    } else {
        Write-Pass "Complete End-to-End Escrow Workflow Verified successfully:"
        Write-Pass "  - On-chain Escrow Registration & Funding Verification"
        Write-Pass "  - Submissions & Client Revision Requests"
        Write-Pass "  - Deadline Extensions (Request & Client Approval)"
        Write-Pass "  - Mutual Settlements (Propose, On-chain Execution & Withdrawal)"
        Write-Pass "  - Dispute Resolution (Open, Evidence Thread, Admin Split Decision & Resolution Tx)"
        Write-Pass "  - Two-way Rating & Review System (Client <-> Freelancer)"
    }
} finally {
    Pop-Location
}

Write-Host "`n==================================================" -ForegroundColor Green
Write-Host " ALL 11 E2E WORKFLOW CHECKS PASSED SUCCESSFULLY! " -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
