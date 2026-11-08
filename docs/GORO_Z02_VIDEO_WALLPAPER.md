# GORO Z02 video wallpaper route

## Why the old GIF path was wrong

The previous Z02 action treated the device like a JieLi colour-screen charging case:
1. Read `SDKMessage.isSupportGif`.
2. Convert the GORO GIF with `GifConverter`.
3. Upload a `.RES` resource.
4. Activate it with `setCurrentScreenSaver`.

The Z02 firmware reports `isSupportGif=false`, so that path correctly refuses the GIF. That flag is not proof that the badge cannot play animated content. It only rejects GIFs in the charging-case screensaver path.

## Device-specific evidence

The ZNP Z02 user guide documents a separate **Custom Wallpapers** workflow:
- Video clips must be 3–8 seconds.
- Up to five historical videos can be saved.
- A multi-image wallpaper accepts 1–10 images and is saved as an AVI carousel.

A public Z02 stock-firmware field guide reports:
- Display geometry: 240 × 296.
- A stock `AVI` resource container.
- Four Motion-JPEG AVI assets.
- A stock `AVI1.AVI` measured at 240 × 296 and 30 FPS.
- RCSP file operations and the stock resource names `AVI` and `WP_CFG.BIN`.

The guide marks the RCSP opcode mapping for selecting and uploading files as inferred, not fully proven. The firmware image is not modified by this app.

## GORO 1.3.0 implementation

When the connected Z02 reports `isSupportGif=false`, GORO now:
1. Keeps the original authored 240 × 296 GIF unchanged.
2. Decodes its frames locally.
3. Loops the animation into a 3-second, 30-FPS Motion-JPEG AVI.
4. Writes the AVI into the Z02 `/AVI/` resource directory through the existing authenticated RCSP file-transfer API.
5. Selects it through the SDK wallpaper API (`setCurrentWallPaper`), not the GIF screensaver API.
6. Reads back the active wallpaper and compares both storage handle and exact path before reporting success.

The remote filename is content-derived so repeated installs do not overwrite an unrelated file. The upload is aborted if the SDK cannot set and verify the intended `/AVI/` destination.

## Verification boundary

Automated tests validate the AVI RIFF structure, MJPEG frame markers, 240 × 296 geometry, 30-FPS timing headers, frame index, and invalid-input rejection. Android CI must still compile and run the emulator suite.

A physical Z02 acceptance test is required to confirm that this firmware accepts the uploaded AVI path and `setCurrentWallPaper` activation. Until that callback and read-back path match on the actual badge, the app must not claim the animation is installed. No firmware update, raw flash write, or device erase is part of this route.
