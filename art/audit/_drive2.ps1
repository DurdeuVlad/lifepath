param([string]$Action, [string]$Arg = "")
Add-Type @'
using System; using System.Runtime.InteropServices;
public class PM {
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint m, IntPtr w, IntPtr l);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool ScreenToClient(IntPtr h, ref POINT p);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
    public struct RECT { public int Left, Top, Right, Bottom; }
    public struct POINT { public int X, Y; }
}
'@
$h = (Get-Process -Id 89332).MainWindowHandle
$WM_KEYDOWN=0x100; $WM_KEYUP=0x101; $WM_CHAR=0x102
$WM_LBUTTONDOWN=0x201; $WM_LBUTTONUP=0x202; $WM_MOUSEMOVE=0x200

function MKLP($vk,$scan,$up) {
    $l = 1 -bor ($scan -shl 16)
    if ($up) { $l = $l -bor (3 -shl 30) }
    return [IntPtr]$l
}
$scans = @{ esc=0x01; c=0x2E; g=0x22; e=0x12; t=0x14; f2=0x3C; w=0x11; a=0x1E; s=0x1F; d=0x20; space=0x39; enter=0x1C; slash=0x35; f5=0x3F }
$vks   = @{ esc=0x1B; c=0x43; g=0x47; e=0x45; t=0x54; f2=0x71; w=0x57; a=0x41; s=0x53; d=0x44; space=0x20; enter=0x0D; slash=0xBF; f5=0x74 }

switch ($Action) {
  "key" {
     $k = $Arg.ToLower()
     [PM]::PostMessage($h, $WM_KEYDOWN, [IntPtr]$vks[$k], (MKLP $vks[$k] $scans[$k] $false)) | Out-Null
     Start-Sleep -Milliseconds 120
     [PM]::PostMessage($h, $WM_KEYUP, [IntPtr]$vks[$k], (MKLP $vks[$k] $scans[$k] $true)) | Out-Null
     Start-Sleep -Milliseconds 600
  }
  "type" {
     foreach ($ch in $Arg.ToCharArray()) {
        [PM]::PostMessage($h, $WM_CHAR, [IntPtr][int][char]$ch, [IntPtr]0) | Out-Null
        Start-Sleep -Milliseconds 25
     }
  }
  "cmd" {
     # open chat via '/', type command text, enter
     [PM]::PostMessage($h, $WM_KEYDOWN, [IntPtr]$vks['slash'], (MKLP $vks['slash'] $scans['slash'] $false)) | Out-Null
     [PM]::PostMessage($h, $WM_KEYUP, [IntPtr]$vks['slash'], (MKLP $vks['slash'] $scans['slash'] $true)) | Out-Null
     Start-Sleep -Milliseconds 600
     foreach ($ch in $Arg.ToCharArray()) {
        [PM]::PostMessage($h, $WM_CHAR, [IntPtr][int][char]$ch, [IntPtr]0) | Out-Null
        Start-Sleep -Milliseconds 25
     }
     Start-Sleep -Milliseconds 200
     [PM]::PostMessage($h, $WM_KEYDOWN, [IntPtr]$vks['enter'], (MKLP $vks['enter'] $scans['enter'] $false)) | Out-Null
     [PM]::PostMessage($h, $WM_KEYUP, [IntPtr]$vks['enter'], (MKLP $vks['enter'] $scans['enter'] $true)) | Out-Null
     Start-Sleep -Milliseconds 800
  }
  "click" {
     $fx,$fy = $Arg.Split(',') | ForEach-Object { [double]$_ }
     $r = New-Object PM+RECT; [PM]::GetClientRect($h, [ref]$r) | Out-Null
     $x = [int](($r.Right)*$fx); $y = [int](($r.Bottom)*$fy)
     $lp = [IntPtr]($y -shl 16 -bor ($x -band 0xFFFF))
     [PM]::PostMessage($h, $WM_MOUSEMOVE, [IntPtr]0, $lp) | Out-Null
     Start-Sleep -Milliseconds 150
     [PM]::PostMessage($h, $WM_LBUTTONDOWN, [IntPtr]1, $lp) | Out-Null
     Start-Sleep -Milliseconds 80
     [PM]::PostMessage($h, $WM_LBUTTONUP, [IntPtr]0, $lp) | Out-Null
     Start-Sleep -Milliseconds 800
  }
  "shot" {
     $r = New-Object PM+RECT; [PM]::GetWindowRect($h, [ref]$r) | Out-Null
     Add-Type -AssemblyName System.Drawing
     $bmp = New-Object Drawing.Bitmap ($r.Right-$r.Left), ($r.Bottom-$r.Top)
     $g = [Drawing.Graphics]::FromImage($bmp)
     $g.CopyFromScreen($r.Left, $r.Top, 0, 0, $bmp.Size)
     $bmp.Save("C:\Users\Vlad\Documents\Github\lifepath\art\audit\_mc_state.png")
  }
}
