import CoreGraphics
import CoreText
import Foundation

let args = CommandLine.arguments.dropFirst()
switch args.first ?? "" {
case "window":
  let windows = CGWindowListCopyWindowInfo([.optionOnScreenOnly, .excludeDesktopElements], kCGNullWindowID) as? [[String: Any]] ?? []
  for window in windows {
    guard (window[kCGWindowOwnerName as String] as? String) == "kDiffViewer",
          (window[kCGWindowLayer as String] as? Int) == 0,
          let number = window[kCGWindowNumber as String] as? Int,
          let bounds = window[kCGWindowBounds as String] as? [String: Any] else { continue }
    let value = { (key: String) in Int((bounds[key] as? Double) ?? 0) }
    print(number, value("X"), value("Y"), value("Width"), value("Height"))
    exit(0)
  }
  exit(1)
case "font":
  let families = CTFontManagerCopyAvailableFontFamilyNames() as? [String] ?? []
  let name = args.dropFirst().first ?? ""
  print(families.contains(name))
case "register":
  let urls = args.dropFirst().map { URL(fileURLWithPath: $0) } as NSArray as CFArray
  CTFontManagerRegisterFontURLs(urls, .user, true) { errors, done in
    (errors as NSArray).forEach { print("register error:", $0) }
    if done { exit(0) }
    return true
  }
  RunLoop.main.run(until: Date().addingTimeInterval(30))
  exit(1)
case "displays":
  var count: UInt32 = 0
  CGGetActiveDisplayList(0, nil, &count)
  var ids = [CGDirectDisplayID](repeating: 0, count: Int(count))
  CGGetActiveDisplayList(count, &ids, &count)
  for id in ids {
    let b = CGDisplayBounds(id)
    let m = CGDisplayCopyDisplayMode(id)
    print(id, id == CGMainDisplayID() ? "main" : "-", b.origin.x, b.origin.y, m?.width ?? 0, m?.height ?? 0, m?.pixelWidth ?? 0, m?.pixelHeight ?? 0)
  }
case "move":
  let values = args.dropFirst().compactMap { Double($0) }
  guard values.count == 2 else { exit(2) }
  CGEvent(mouseEventSource: nil, mouseType: .mouseMoved, mouseCursorPosition: CGPoint(x: values[0], y: values[1]), mouseButton: .left)?.post(tap: .cghidEventTap)
case "click":
  let values = args.dropFirst().compactMap { Double($0) }
  guard values.count == 2 else { exit(2) }
  let point = CGPoint(x: values[0], y: values[1])
  for type in [CGEventType.mouseMoved, .leftMouseDown, .leftMouseUp] {
    CGEvent(mouseEventSource: nil, mouseType: type, mouseCursorPosition: point, mouseButton: .left)?.post(tap: .cghidEventTap)
    usleep(80_000)
  }
case "key":
  // key <virtual key code> [cmd]
  guard let code = args.dropFirst().first.flatMap({ CGKeyCode($0) }) else { exit(2) }
  let flags: CGEventFlags = args.dropFirst(2).first == "cmd" ? .maskCommand : []
  for down in [true, false] {
    let event = CGEvent(keyboardEventSource: nil, virtualKey: code, keyDown: down)
    event?.flags = flags
    event?.post(tap: .cghidEventTap)
    usleep(80_000)
  }
default:
  exit(2)
}
