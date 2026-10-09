param(
    [Parameter(Position=0)][string]$Action,
    [Parameter(Position=1)][string]$A = "",
    [Parameter(Position=2)][string]$B = "",
    [Parameter(Position=3)][string]$C = ""
)

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class U32 {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int n);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(int f, int dx, int dy, int d, int e);
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, int f, int e);
    [DllImport("user32.dll")] public static extern uint SendInput(uint n, INPUT[] p, int cb);
    public struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct MOUSEINPUT { public int dx, dy, mouseData, dwFlags, time; public IntPtr ex; }
    [StructLayout(LayoutKind.Explicit)] public struct INPUT { [FieldOffset(0)] public int type; [FieldOffset(8)] public MOUSEINPUT mi; }
    public static void WheelNotch(int notches) {
        int n = notches < 0 ? -notches : notches;
        INPUT[] ev = new INPUT[n];
        for (int i = 0; i < n; i++) {
            ev[i].type = 0;
            ev[i].mi.dwFlags = 0x0800;
            ev[i].mi.mouseData = notches < 0 ? -120 : 120;
        }
        SendInput((uint)n, ev, Marshal.SizeOf(typeof(INPUT)));
    }
}
"@
Add-Type -AssemblyName System.Drawing

$script:hwnd = $null
$script:rect = $null

function FindWindow {
    $p = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like 'Minecraft* 1.21.1*' -and $_.MainWindowTitle -notlike '*NeoForge*' }
    if (-not $p) { Write-Output "NO-WINDOW"; exit 1 }
    $script:hwnd = $p.MainWindowHandle
}

function Restore {
    [U32]::ShowWindow($script:hwnd, 9) | Out-Null   # SW_RESTORE
    [U32]::SetForegroundWindow($script:hwnd) | Out-Null
    Start-Sleep -Milliseconds 600
    $r = New-Object U32+RECT
    [U32]::GetWindowRect($script:hwnd, [ref]$r) | Out-Null
    $script:rect = $r
}

function Shot($name) {
    $r = $script:rect
    $bmp = New-Object System.Drawing.Bitmap ($r.Right - $r.Left), ($r.Bottom - $r.Top)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($r.Left, $r.Top, 0, 0, $bmp.Size)
    $bmp.Save("C:\Users\Vlad\Documents\Github\lifepath\run\$name.png")
    $bmp.Dispose()
    Write-Output "shot $name $($r.Right - $r.Left)x$($r.Bottom - $r.Top) at $($r.Left),$($r.Top)"
}

function ClickAt($x, $y) {
    [U32]::SetCursorPos([int]$x, [int]$y) | Out-Null
    Start-Sleep -Milliseconds 250
    [U32]::mouse_event(0x0002, 0, 0, 0, 0)
    Start-Sleep -Milliseconds 90
    [U32]::mouse_event(0x0004, 0, 0, 0, 0)
    Start-Sleep -Milliseconds 600
}

function MoveAt($x, $y) {
    [U32]::SetCursorPos([int]$x, [int]$y) | Out-Null
    Start-Sleep -Milliseconds 700
}

function PressKey($vk) {
    [U32]::keybd_event([byte][int]$vk, 0, 0, 0)
    Start-Sleep -Milliseconds 90
    [U32]::keybd_event([byte][int]$vk, 0, 2, 0)  # KEYUP
    Start-Sleep -Milliseconds 600
}

function Wheel($delta) {
    [U32]::WheelNotch([int]([int]$delta / 120))
    Start-Sleep -Milliseconds 700
}

function DragTo($x1, $y1, $x2, $y2) {
    [U32]::SetCursorPos([int]$x1, [int]$y1) | Out-Null
    Start-Sleep -Milliseconds 250
    [U32]::mouse_event(0x0002, 0, 0, 0, 0)   # LEFTDOWN
    Start-Sleep -Milliseconds 200
    $steps = 10
    for ($i = 1; $i -le $steps; $i++) {
        $xi = [int]($x1 + ($x2 - $x1) * $i / $steps)
        $yi = [int]($y1 + ($y2 - $y1) * $i / $steps)
        [U32]::SetCursorPos($xi, $yi) | Out-Null
        Start-Sleep -Milliseconds 60
    }
    Start-Sleep -Milliseconds 150
    [U32]::mouse_event(0x0004, 0, 0, 0, 0)   # LEFTUP
    Start-Sleep -Milliseconds 600
}

FindWindow
Restore

switch ($Action) {
    "shot"   { Shot $A }
    "click"  { ClickAt $A $B }
    "move"   { MoveAt $A $B }
    "key"    { PressKey $A }
    "wheel"  { Wheel $A }
    "drag"   { DragTo $A $B $C $args[0] }
    "rect"   { Write-Output "rect $($script:rect.Left),$($script:rect.Top) $($script:rect.Right)x$($script:rect.Bottom)" }
    default  { Write-Output "usage: uitest.ps1 shot|click x y|move x y|key vk|wheel delta|rect" }
}
