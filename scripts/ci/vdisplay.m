// The runner's display has no 2x modes, so this adds a 2x virtual display and makes it the main display.
// CGVirtualDisplay is not a public API, so a macOS update can break this without notice.
#import <CoreGraphics/CoreGraphics.h>
#import <Foundation/Foundation.h>

@interface CGVirtualDisplayDescriptor : NSObject
@property(retain, nonatomic) dispatch_queue_t queue;
@property(copy, nonatomic) NSString *name;
@property(nonatomic) unsigned int maxPixelsWide;
@property(nonatomic) unsigned int maxPixelsHigh;
@property(nonatomic) CGSize sizeInMillimeters;
@property(nonatomic) unsigned int productID;
@property(nonatomic) unsigned int vendorID;
@property(nonatomic) unsigned int serialNum;
@end

@interface CGVirtualDisplayMode : NSObject
- (instancetype)initWithWidth:(NSUInteger)width height:(NSUInteger)height refreshRate:(double)refreshRate;
@end

@interface CGVirtualDisplaySettings : NSObject
@property(retain, nonatomic) NSArray *modes;
@property(nonatomic) unsigned int hiDPI;
@end

@interface CGVirtualDisplay : NSObject
- (instancetype)initWithDescriptor:(CGVirtualDisplayDescriptor *)descriptor;
- (BOOL)applySettings:(CGVirtualDisplaySettings *)settings;
@property(readonly, nonatomic) CGDirectDisplayID displayID;
@end

int main(void) {
  @autoreleasepool {
    const unsigned int width = 1600, height = 1000;
    CGVirtualDisplayDescriptor *descriptor = [CGVirtualDisplayDescriptor new];
    descriptor.queue = dispatch_get_main_queue();
    descriptor.name = @"Screenshot";
    descriptor.maxPixelsWide = width * 2;
    descriptor.maxPixelsHigh = height * 2;
    descriptor.sizeInMillimeters = CGSizeMake(345, 216);
    descriptor.productID = 0x1234;
    descriptor.vendorID = 0x3456;
    descriptor.serialNum = 1;
    CGVirtualDisplay *display = [[CGVirtualDisplay alloc] initWithDescriptor:descriptor];
    if (display == nil) { fprintf(stderr, "could not create the virtual display\n"); return 1; }
    CGVirtualDisplaySettings *settings = [CGVirtualDisplaySettings new];
    settings.hiDPI = 1;
    settings.modes = @[[[CGVirtualDisplayMode alloc] initWithWidth:width height:height refreshRate:60]];
    if (![display applySettings:settings]) { fprintf(stderr, "could not apply the settings\n"); return 1; }
    CGDirectDisplayID displayID = display.displayID;
    printf("virtual display %u\n", displayID);
    fflush(stdout);
    // The new display is not ready to be moved right after applySettings.
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 3 * NSEC_PER_SEC), dispatch_get_main_queue(), ^{
      CGDirectDisplayID physicalID = CGMainDisplayID();
      CGDisplayConfigRef config;
      CGBeginDisplayConfiguration(&config);
      // Both origins move in one transaction; moving only the new display leaves the old one at the origin as main.
      CGConfigureDisplayOrigin(config, displayID, 0, 0);
      CGConfigureDisplayOrigin(config, physicalID, width, 0);
      CGError error = CGCompleteDisplayConfiguration(config, kCGConfigureForSession);
      CGDisplayModeRef mode = CGDisplayCopyDisplayMode(displayID);
      printf("made main: error %d, main %u, virtual mode %zu x %zu (%zu x %zu px)\n", error, CGMainDisplayID(), CGDisplayModeGetWidth(mode), CGDisplayModeGetHeight(mode), CGDisplayModeGetPixelWidth(mode), CGDisplayModeGetPixelHeight(mode));
      fflush(stdout);
    });
    dispatch_main();
  }
}
