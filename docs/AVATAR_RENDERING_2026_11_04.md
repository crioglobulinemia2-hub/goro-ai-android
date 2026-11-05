# Avatar rendering continuation, 4 November 2026

## Implemented

`GoroAvatarView` uses the TextureView constructor provided by Filament 1.70.0
ModelViewer. The TextureView and UiHelper are non-opaque, the Filament view uses
TRANSLUCENT blending, and the clear color has zero alpha. This keeps the robot
inside Android's normal view hierarchy so the room and UI overlays can compose
with it. No replacement character or geometry was generated for this change.

The Choreographer loop is suspended when the avatar, an ancestor, or the window
is hidden. It restarts as one chain when visible, and is canceled on detach.
Previously, opening Settings or another page still performed animation and
frame work for the hidden home avatar. Frame-loop regression tests cover late
callbacks after hiding, repeated visibility notifications, resuming, and
deactivation during a frame.

Canonical and mapped facial weights now reuse two buffers allocated per avatar.
Both buffers are cleared between frames, including any extra exported targets,
so returning from speech to silence does not retain stale mouth/eye weights.

Filament ModelViewer 1.70.0 destroys its engine, loaders, model and owned render
objects from its SurfaceView/TextureView detach listener. This was confirmed
against the installed library bytecode; no second engine destruction was added.

## Verification boundary

Compilation and JVM tests do not verify Vulkan alpha compositing or GPU behavior.
A capture on Android must confirm that the room is visible through the clear
area, the character retains its materials, and dialogue/cards stay above it.
The upstream ModelViewer's installed SurfaceCallback calls the one-argument createSwapChain.
Installed Engine bytecode confirms that this overload passes flags zero, while
UiHelper.getSwapChainFlags returns one for non-opaque surfaces. ModelViewer is
final and has no supported public setter for its private swapchain. The pinned
source adaptation `GoroModelViewer` now passes `uiHelper.getSwapChainFlags()` at
swapchain creation instead of relying on reflection into private state. The
effective alpha behavior must still be verified on the target renderer.
No physical device validation or visual-reference acceptance is claimed here.

## TRELLIS integration contract

A static textured GLB is a geometry source, not yet the animated final avatar.
Preserve that original export. The authored result belongs at
`art/goro/goro_rigged.glb` and is copied unchanged by `scripts/prepare_avatar.py`.
It must include the fourteen named skin joints in `AvatarRigContract`, embedded
PBR resources, the `GORO_Face_Morphs` node with the ten named facial targets,
and six nonempty clips named `idle`, `listening`, `thinking`, `speaking`,
`confirming`, and `alert`. The mesh primitives also require UVs and skin weights.
Binding bones without suitable skin weights or adding empty morph targets would
not provide the required motion and does not establish completion.

The existing development model is not the approved character. A successful
contract check is separate from faithful visual comparison and device testing.

## Pinned source adaptation

`app/src/main/java/com/google/android/filament/utils/GoroModelViewer.kt` comes from
[Filament v1.70.0 ModelViewer.kt](https://github.com/google/filament/blob/v1.70.0/android/filament-utils-android/src/main/java/com/google/android/filament/utils/ModelViewer.kt).
The original downloaded source SHA-256 is
`ddced9234a0c562cd45707ed3e1a3503a55b04e5a3e88c65c0efa665241235fe`.
The changes are the class/self-reference rename to `GoroModelViewer` and passing
`uiHelper.swapChainFlags` to `engine.createSwapChain`. The original copyright and
Apache header are preserved, and the license text is included in APK assets at
`licenses/filament-apache-2.0.txt`.

The source uses coroutines 1.9.0, matching Filament's own POM. They are declared
explicitly at compile scope because filament-utils exposes them at runtime
scope. This does not change the selected versions. AGP's existing built-in
Kotlin support compiles the adapter; no alternative toolchain flags were added.

Local verification of the adapter is blocked before compilation because the
offline cache lacks `kotlin-build-tools-impl:2.2.10`. CI must resolve that public
dependency, compile the adapter, and run the full JVM and Android checks. The
facial-buffer mapping is independently compilable Java and has five passing
JUnit tests, including clearing a reused buffer when speech ends.

## Failed model setup and explicit cleanup

The source adapter now exposes idempotent `destroy()`, also called by its detach
listener. `GoroAvatarView` releases its owned lights and invokes this cleanup
when model/lighting setup fails while the view is still attached. Previously
the catch block dropped the viewer reference and retained native resources
until the screen detached. A null glTF asset is now treated as a load failure
instead of reporting a loaded model. The adapter ignores rendering after
cleanup, and a later detach does not destroy the engine twice.

`AvatarReattachTest` covers explicit cleanup twice followed by view removal,
in addition to rendering after repeated avatar removal and reattachment.
These native tests require an Android Vulkan renderer; successful JVM or asset
checks alone do not establish that the native cleanup tests passed.
