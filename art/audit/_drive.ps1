param([string]$Action, [string]$Arg = "")
Add-Type @'
using System;
using System.Runtime.InteropServices;
public class Win32 {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT r);
    [DllImport("user32.dll")] public static extern void SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint d, IntPtr e);
    public struct RECT { public int Left, Top, Right, Bottom; }
}
'@
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$p = Get-Process -Id 19348
function Rect { $r = New-Object Win32+RECT; [Win32]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null; return $r }

switch ($Action) {
  "focus"  { [Win32]::SetForegroundWindow($p.MainWindowHandle) | Out-Null; Start-Sleep -Milliseconds 400 }
  "click"  { $fx,$fy = $Arg.Split(',') | ForEach-Object { [double]$_ }
             $r = Rect; [Win32]::SetCursorPos([int]($r.Left+($r.Right-$r.Left)*$fx), [int]($r.Top+($r.Bottom-$r.Top)*$fy))
             Start-Sleep -Milliseconds 250
             [Win32]::mouse_event(0x02,0,0,0,[IntPtr]::Zero); Start-Sleep -Milliseconds 80
             [Win32]::mouse_event(0x04,0,0,0,[IntPtr]::Zero); Start-Sleep -Milliseconds 700 }
  "key"    { [Win32]::SetForegroundWindow($p.MainWindowHandle) | Out-Null; Start-Sleep -Milliseconds 200
             [System.Windows.Forms.SendKeys]::SendWait($Arg); Start-Sleep -Milliseconds 400 }
  "cmd"    { [Win32]::SetForegroundWindow($p.MainWindowHandle) | Out-Null; Start-Sleep -Milliseconds 300
             [System.Windows.Forms.SendKeys]::SendWait("t"); Start-Sleep -Milliseconds 600
             foreach ($c in ("/" + $Arg).ToCharArray()) {
                 $s = [string]$c
                 if ('+^%~(){}[]'.Contains($c)) { $s = '{' + $c + '}' }
                 [System.Windows.Forms.SendKeys]::SendWait($s); Start-Sleep -Milliseconds 30
             }
             Start-Sleep -Milliseconds 300
             [System.Windows.Forms.SendKeys]::SendWait("{ENTER}"); Start-Sleep -Milliseconds 800 }
  "type"   { [Win32]::SetForegroundWindow($p.MainWindowHandle) | Out-Null; Start-Sleep -Milliseconds 200
             foreach ($c in $Arg.ToCharArray()) {
                 $s = [string]$c
                 if ('+^%~(){}[]'.Contains($c)) { $s = '{' + $c + '}' }
                 [System.Windows.Forms.SendKeys]::SendWait($s); Start-Sleep -Milliseconds 30
             } }
  "shot"   { $r = Rect
             $bmp = New-Object Drawing.Bitmap ($r.Right-$r.Left), ($r.Bottom-$r.Top)
             $g = [Drawing.Graphics]::FromImage($bmp)
             $g.CopyFromScreen($r.Left, $r.Top, 0, 0, $bmp.Size)
             $bmp.Save("C:\Users\Vlad\Documents\Github\lifepath\art\audit\_mc_state.png") }
}
