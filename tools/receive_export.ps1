$out = Join-Path $env:TEMP 'rbx\legacy'
$l = New-Object System.Net.HttpListener
$l.Prefixes.Add('http://127.0.0.1:18723/')
$l.Start()
"listening"
while ($l.IsListening) {
  $c = $l.GetContext()
  $rel = $c.Request.QueryString['p']
  if ($rel -eq 'STOP') { $c.Response.Close(); break }
  $ms = New-Object System.IO.MemoryStream
  $c.Request.InputStream.CopyTo($ms)
  $dest = Join-Path $out ($rel -replace '[^A-Za-z0-9/._ -]', '_')
  New-Item -ItemType Directory -Force (Split-Path $dest) | Out-Null
  [IO.File]::WriteAllBytes($dest, $ms.ToArray())
  "saved $rel $($ms.Length)"
  $b = [Text.Encoding]::UTF8.GetBytes('ok'); $c.Response.OutputStream.Write($b, 0, 2); $c.Response.Close()
}
$l.Stop()
