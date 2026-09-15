param([string]$Message)
Set-Location (Split-Path $PSScriptRoot -Parent)
$env:GIT_AUTHOR_NAME = "changchengfeng"
$env:GIT_AUTHOR_EMAIL = "changchengfeng001@gmail.com"
$env:GIT_COMMITTER_NAME = "changchengfeng"
$env:GIT_COMMITTER_EMAIL = "changchengfeng001@gmail.com"
$tree = git rev-parse "HEAD^{tree}"
$new = & git.exe commit-tree $tree -p HEAD -m $Message
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git reset --hard $new
