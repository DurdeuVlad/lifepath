param([double]$fx,[double]$fy)
Add-Type @'
using System; using System.Runtime.InteropServices;
public class RC {
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint x, uint y, uint d, IntPtr e);
    public struct RECT { public int Left, Top, Right, Bottom; }
}
'@
$h = (Get-Process -Id 89332).MainWindowHandle
$r = New-Object RC+RECT; [RC]::GetWindowRect($h,[ref]$r) | Out-Null
$x = [int]($r.Left + ($r.Right-$r.Left)*$fx); $y = [int]($r.Top + ($r.Bottom-$r.Top)*$fy)
[RC]::SetCursorPos($x,$y) | Out-Null
Start-Sleep -Milliseconds 200
[RC]::mouse_event(0x02,0,0,0,[IntPtr]0); Start-Sleep -Milliseconds 60
[RC]::mouse_event(0x04,0,0,0,[IntPtr]0); Start-Sleep -Milliseconds 500
