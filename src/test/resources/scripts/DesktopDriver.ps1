<#
  =====================================================================================
   DesktopDriver.ps1 - Windows UI Automation "driver" for the AVAIL Desktop (WPF) app
  =====================================================================================
   Started ONCE per test class by desktop.DesktopDriver (Java) and kept running.
   Java sends one JSON command per line on STDIN, this script answers one JSON line on STDOUT.

   Why not Playwright?  AVAIL Desktop is a native WPF application. Playwright only automates
   browsers, so desktop apps are read and driven through Windows UI Automation (built into
   Windows, nothing to install) - the same technique the AVAIL Stream framework uses in
   AvailDesktop.ps1, but as a long-running session (no new PowerShell per call = much faster).

   REQUEST  (one line):  {"cmd":"find","parent":"w0","automationId":"ContentListView","all":true}
   RESPONSE (one line):  {"ok":true,"result":[{"id":"e12","controlType":"List",...}]}
                         {"ok":false,"error":"Element e12 is no longer available"}

   COMMANDS
     ping                                   -> "pong"
     attach   title, automationId, timeoutMs -> info of the top-level window (handle "w0"); automationId optional
     find     parent, automationId, name, nameContains, controlType, className, scope, all
                                            -> list of element infos (empty list = not found)
     info     id                            -> fresh info of one element (fails if it is gone)
     text     id                            -> visible text (TextBlock inside, Value, or Name)
     click    id                            -> Invoke / Toggle / Select / Expand, else mouse click
     select   id                            -> SelectionItemPattern.Select()
     setValue id, value                     -> ValuePattern.SetValue(value) (text boxes)
     expand   id / collapse id              -> ExpandCollapsePattern
     show     id                            -> maximize + bring to front, returns bounds
     doubleClick id                         -> mouse double-click (only if AVAIL Desktop is the foreground window)
     scroll   id, verticalPercent           -> ScrollPattern (virtualized lists), returns scroll state
     scrollIntoView id                      -> ScrollItemPattern
     isForeground                           -> true if AVAIL Desktop is the active window

   SAFETY: commands that need the REAL mouse (doubleClick, click without UI Automation pattern) first check
   that AVAIL Desktop is the foreground window and refuse otherwise - a click never lands in another app.
     release                                -> forgets all element handles (memory)
     quit                                   -> ends the script

   ELEMENT INFO fields: id, controlType, name, automationId, className, text, enabled, offscreen,
                        x, y, width, height (physical pixels)

   TEST IT BY HAND (PowerShell, from the project folder):
     '{"cmd":"attach","title":"AVAIL Desktop","timeoutMs":5000}' | powershell -NoProfile -ExecutionPolicy Bypass -File src\test\resources\scripts\DesktopDriver.ps1

   WHEN DO I NEED TO CHANGE THIS FILE?
     Only to add a new kind of action (a new UI Automation pattern). Locators (AutomationId,
     names) are NOT here - they are in the Java page objects under src/test/java/pages.
  =====================================================================================
#>

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding  = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes

$AE     = [System.Windows.Automation.AutomationElement]
$Scope  = [System.Windows.Automation.TreeScope]
$CT     = [System.Windows.Automation.ControlType]
$walker = [System.Windows.Automation.TreeWalker]::RawViewWalker

# Win32 helpers: maximize / foreground / mouse click (only used when no UI Automation pattern fits)
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class DesktopWin32 {
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
}
'@

# SAFETY: a real mouse click lands on whatever window is in FRONT. Only click when AVAIL Desktop is the
# foreground window - otherwise fail, so a click never hits another app (e.g. the browser you are using).
function Assert-Foreground {
    $w = $script:elements['w0']
    if ($null -eq $w) { throw 'Not attached' }
    if ([DesktopWin32]::GetForegroundWindow() -ne [IntPtr]$w.Current.NativeWindowHandle) {
        throw 'AVAIL Desktop is not the foreground window - mouse click refused (another app is in front / the PC is in use). Close other windows or set desktop.bringToFront=true and do not use the PC during the run.'
    }
}

function Mouse-Click($el, [int]$times) {
    Assert-Foreground
    $r = $el.Current.BoundingRectangle
    if ([double]::IsInfinity($r.X) -or $r.Width -le 0) { throw 'Element is not on screen - cannot click it with the mouse' }
    [void][DesktopWin32]::SetCursorPos([int]($r.X + $r.Width / 2), [int]($r.Y + $r.Height / 2))
    for ($i = 0; $i -lt $times; $i++) {
        [DesktopWin32]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)   # left down
        [DesktopWin32]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)   # left up
        Start-Sleep -Milliseconds 90
    }
}

# element handles: "w0" = main window, "e1", "e2", ... = found elements
$script:elements = @{}
$script:nextId = 1

function New-Handle($el) {
    $id = 'e' + $script:nextId
    $script:nextId++
    $script:elements[$id] = $el
    return $id
}

function Get-Element([string]$id) {
    if (-not $script:elements.ContainsKey($id)) { throw "Unknown element handle '$id' (call attach first)" }
    return $script:elements[$id]
}

# Visible text: WPF drops the first "_" from Name (access key), so prefer the TextBlock inside,
# then a ValuePattern value (text boxes), then the Name.
function Get-Text($el) {
    $child = $walker.GetFirstChild($el)
    while ($null -ne $child) {
        if ($child.Current.ControlType -eq $CT::Text -and $child.Current.Name -and $child.Current.Name.Trim()) { return $child.Current.Name }
        $child = $walker.GetNextSibling($child)
    }
    $vp = $null
    if ($el.TryGetCurrentPattern([System.Windows.Automation.ValuePattern]::Pattern, [ref]$vp)) {
        $v = $vp.Current.Value
        if ($v) { return $v }
    }
    return $el.Current.Name
}

function Get-Info($el, [string]$id) {
    $c = $el.Current
    $r = $c.BoundingRectangle
    $finite = -not ([double]::IsInfinity($r.X) -or [double]::IsNaN($r.X))
    return [ordered]@{
        id           = $id
        controlType  = $c.ControlType.ProgrammaticName.Replace('ControlType.', '')
        name         = $c.Name
        automationId = $c.AutomationId
        className    = $c.ClassName
        text         = (Get-Text $el)
        enabled      = $c.IsEnabled
        offscreen    = $c.IsOffscreen
        x            = $(if ($finite) { [int]$r.X } else { 0 })
        y            = $(if ($finite) { [int]$r.Y } else { 0 })
        width        = $(if ($finite) { [int]$r.Width } else { 0 })
        height       = $(if ($finite) { [int]$r.Height } else { 0 })
    }
}

function Find-Elements($req) {
    $parent = Get-Element ([string]$req.parent)
    $conds = New-Object System.Collections.Generic.List[System.Windows.Automation.Condition]
    if ($req.automationId) { $conds.Add((New-Object System.Windows.Automation.PropertyCondition($AE::AutomationIdProperty, [string]$req.automationId))) }
    if ($req.name)         { $conds.Add((New-Object System.Windows.Automation.PropertyCondition($AE::NameProperty, [string]$req.name))) }
    if ($req.className)    { $conds.Add((New-Object System.Windows.Automation.PropertyCondition($AE::ClassNameProperty, [string]$req.className))) }
    if ($req.controlType) {
        $ctField = $CT.GetField([string]$req.controlType)
        if ($null -eq $ctField) { throw "Unknown controlType '$($req.controlType)' (use e.g. Button, Text, List, ListItem, Edit)" }
        $conds.Add((New-Object System.Windows.Automation.PropertyCondition($AE::ControlTypeProperty, $ctField.GetValue($null))))
    }
    if ($conds.Count -eq 0)     { $cond = [System.Windows.Automation.Condition]::TrueCondition }
    elseif ($conds.Count -eq 1) { $cond = $conds[0] }
    else                        { $cond = New-Object System.Windows.Automation.AndCondition(,$conds.ToArray()) }

    $where = if ($req.scope -eq 'children') { $Scope::Children } else { $Scope::Descendants }
    $found = $parent.FindAll($where, $cond)

    $result = New-Object System.Collections.ArrayList
    foreach ($el in $found) {
        if ($req.nameContains) {
            $t = Get-Text $el
            if (($el.Current.Name -notlike "*$($req.nameContains)*") -and ($t -notlike "*$($req.nameContains)*")) { continue }
        }
        if ($req.textEquals) {
            if ((Get-Text $el) -ne [string]$req.textEquals) { continue }
        }
        [void]$result.Add((Get-Info $el (New-Handle $el)))
        if (-not $req.all) { break }
    }
    return ,$result
}

function Try-Pattern($el, $pattern) {
    $p = $null
    if ($el.TryGetCurrentPattern($pattern, [ref]$p)) { return $p }
    return $null
}

function Click-Element($el) {
    $p = Try-Pattern $el ([System.Windows.Automation.InvokePattern]::Pattern)
    if ($p) { $p.Invoke(); return 'invoke' }
    $p = Try-Pattern $el ([System.Windows.Automation.TogglePattern]::Pattern)
    if ($p) { $p.Toggle(); return 'toggle' }
    $p = Try-Pattern $el ([System.Windows.Automation.SelectionItemPattern]::Pattern)
    if ($p) { $p.Select(); return 'select' }
    $p = Try-Pattern $el ([System.Windows.Automation.ExpandCollapsePattern]::Pattern)
    if ($p) { $p.Expand(); return 'expand' }
    # last resort: real mouse click in the middle of the element - ONLY if AVAIL Desktop is in front
    Mouse-Click $el 1
    return 'mouse'
}

function Handle($req) {
    switch ([string]$req.cmd) {
        'ping'   { return 'pong' }
        'attach' {
            $timeout = if ($req.timeoutMs) { [int]$req.timeoutMs } else { 30000 }
            $deadline = (Get-Date).AddMilliseconds($timeout)
            # title AND (optional) AutomationId: the splash screen has the same title "AVAIL Desktop" but no
            # AutomationId - only the real main window has "mMainWindow"
            $cond = New-Object System.Windows.Automation.PropertyCondition($AE::NameProperty, [string]$req.title)
            if ($req.automationId) {
                $cond = New-Object System.Windows.Automation.AndCondition($cond,
                    (New-Object System.Windows.Automation.PropertyCondition($AE::AutomationIdProperty, [string]$req.automationId)))
            }
            do {
                $win = $AE::RootElement.FindFirst($Scope::Children, $cond)
                if ($null -ne $win) { $script:elements['w0'] = $win; return (Get-Info $win 'w0') }
                Start-Sleep -Milliseconds 500
            } while ((Get-Date) -lt $deadline)
            throw "Window '$($req.title)' not found - is AVAIL Desktop running and logged in?"
        }
        'find'     { return (Find-Elements $req) }
        'info'     { $el = Get-Element ([string]$req.id); return (Get-Info $el ([string]$req.id)) }
        'text'     { return (Get-Text (Get-Element ([string]$req.id))) }
        'click'    { return (Click-Element (Get-Element ([string]$req.id))) }
        'select'   {
            $p = Try-Pattern (Get-Element ([string]$req.id)) ([System.Windows.Automation.SelectionItemPattern]::Pattern)
            if (-not $p) { throw 'Element cannot be selected (no SelectionItemPattern)' }
            $p.Select(); return 'selected'
        }
        'setValue' {
            $p = Try-Pattern (Get-Element ([string]$req.id)) ([System.Windows.Automation.ValuePattern]::Pattern)
            if (-not $p) { throw 'Element does not accept text (no ValuePattern)' }
            $p.SetValue([string]$req.value); return 'set'
        }
        'expand'   {
            $p = Try-Pattern (Get-Element ([string]$req.id)) ([System.Windows.Automation.ExpandCollapsePattern]::Pattern)
            if (-not $p) { throw 'Element cannot be expanded' }
            $p.Expand(); return 'expanded'
        }
        'collapse' {
            $p = Try-Pattern (Get-Element ([string]$req.id)) ([System.Windows.Automation.ExpandCollapsePattern]::Pattern)
            if (-not $p) { throw 'Element cannot be collapsed' }
            $p.Collapse(); return 'collapsed'
        }
        'show'     {
            $el = Get-Element ([string]$req.id)
            $h = [IntPtr]$el.Current.NativeWindowHandle
            [void][DesktopWin32]::ShowWindow($h, 3)                                   # SW_MAXIMIZE
            [DesktopWin32]::keybd_event(0x12, 0, 0, [UIntPtr]::Zero)                  # ALT down/up lifts the
            [DesktopWin32]::keybd_event(0x12, 0, 2, [UIntPtr]::Zero)                  # foreground lock
            [void][DesktopWin32]::SetForegroundWindow($h)
            Start-Sleep -Milliseconds 800
            return (Get-Info $el ([string]$req.id))
        }
        'doubleClick' {
            # e.g. opening a channel tile (tiles have no Invoke pattern) - mouse, guarded by Assert-Foreground
            $el = Get-Element ([string]$req.id)
            $si = Try-Pattern $el ([System.Windows.Automation.ScrollItemPattern]::Pattern)
            if ($si) { $si.ScrollIntoView(); Start-Sleep -Milliseconds 300 }
            Mouse-Click $el 2
            return 'doubleClicked'
        }
        'scrollIntoView' {
            $si = Try-Pattern (Get-Element ([string]$req.id)) ([System.Windows.Automation.ScrollItemPattern]::Pattern)
            if (-not $si) { throw 'Element cannot be scrolled into view (no ScrollItemPattern)' }
            $si.ScrollIntoView(); return 'scrolled'
        }
        'scroll' {
            # ScrollPattern on a list: verticalPercent 0..100 (virtualized lists only create the tiles you scroll to)
            $p = Try-Pattern (Get-Element ([string]$req.id)) ([System.Windows.Automation.ScrollPattern]::Pattern)
            if (-not $p) { throw 'Element cannot scroll (no ScrollPattern)' }
            if ($p.Current.VerticallyScrollable) { $p.SetScrollPercent(-1, [double]$req.verticalPercent) }
            return [ordered]@{ scrollable = $p.Current.VerticallyScrollable; percent = $p.Current.VerticalScrollPercent; viewSize = $p.Current.VerticalViewSize }
        }
        'isForeground' {
            $w = $script:elements['w0']
            return ([DesktopWin32]::GetForegroundWindow() -eq [IntPtr]$w.Current.NativeWindowHandle)
        }
        'release'  { $w = $script:elements['w0']; $script:elements = @{}; if ($w) { $script:elements['w0'] = $w }; return 'released' }
        default    { throw "Unknown command '$($req.cmd)'" }
    }
}

# ---- main loop: one JSON request per line -> one JSON response per line ------------------
while ($true) {
    $line = [Console]::In.ReadLine()
    if ($null -eq $line) { break }                     # Java closed the pipe
    if ($line.Trim() -eq '') { continue }
    try {
        $req = $line | ConvertFrom-Json
        if ($req.cmd -eq 'quit') { [Console]::Out.WriteLine('{"ok":true,"result":"bye"}'); [Console]::Out.Flush(); break }
        if ($req.cmd -eq 'find') {
            # always a JSON array, also for 0 or 1 element (PowerShell would unwrap a 1-element list)
            $res = [object[]]@(Handle $req)
            $json = ConvertTo-Json -InputObject @{ ok = $true; result = $res } -Compress -Depth 6
            if ($res.Count -eq 0) { $json = '{"ok":true,"result":[]}' }
        } else {
            $res = Handle $req
            $json = ConvertTo-Json -InputObject @{ ok = $true; result = $res } -Compress -Depth 6
        }
    } catch [System.Windows.Automation.ElementNotAvailableException] {
        $json = ConvertTo-Json -InputObject @{ ok = $false; error = 'Element is no longer available (the app redrew it) - find it again' } -Compress
    } catch {
        $json = ConvertTo-Json -InputObject @{ ok = $false; error = $_.Exception.Message } -Compress
    }
    # echo the request number so Java never mixes up a late answer with the next command
    if ($req -and $req.rid) { $json = '{"rid":' + [long]$req.rid + ',' + $json.Substring(1) }
    [Console]::Out.WriteLine($json)
    [Console]::Out.Flush()
}
