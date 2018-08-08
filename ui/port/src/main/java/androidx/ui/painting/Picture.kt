package androidx.ui.painting

// / An object representing a sequence of recorded graphical operations.
// /
// / To create a [Picture], use a [PictureRecorder].
// /
// / A [Picture] can be placed in a [Scene] using a [SceneBuilder], via
// / the [SceneBuilder.addPicture] method. A [Picture] can also be
// / drawn into a [Canvas], using the [Canvas.drawPicture] method.
// TODO(Migration/Andrey): Real logic is in the native code. What should we do?
class Picture /*extends NativeFieldWrapperClass2 */ {

    // / This class is created by the engine, and should not be instantiated
    // / or extended directly.
    // /
    // / To create a [Picture], use a [PictureRecorder].
    private constructor()

    // / Creates an image from this picture.
    // /
    // / The picture is rasterized using the number of pixels specified by the
    // / given width and height.
    // /
    // / Although the image is returned synchronously, the picture is actually
    // / rasterized the first time the image is drawn and then cached.
    // TODO(Migration/Andrey): Native code. also needs Image class
//    fun toImage(width : Int, height : Int) : Image {
//        native 'Picture_toImage';
//    }

    // / Release the resources used by this object. The object is no longer usable
    // / after this method is called.
    fun dispose() {
        TODO()
//        native 'Picture_dispose';
    }
}