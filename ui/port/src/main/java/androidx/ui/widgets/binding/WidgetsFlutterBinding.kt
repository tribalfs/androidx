package androidx.ui.widgets.binding

import androidx.ui.engine.window.Window
import androidx.ui.foundation.binding.BindingBaseImpl
import androidx.ui.gestures.binding.GestureBinding
import androidx.ui.gestures2.GestureBinding2
import androidx.ui.rendering.binding.RendererBindingImpl
import androidx.ui.scheduler.binding.SchedulerBindingImpl
import androidx.ui.services.ServicesBindingImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * A concrete binding for applications based on the Widgets framework.
 * This is the glue that binds the framework to the Flutter engine.
 */
class WidgetsFlutterBinding {
// TODO(Migration/Filip): extends BindingBase with GestureBinding, ServicesBinding, SchedulerBinding, PaintingBinding, RendererBinding, WidgetsBinding

    companion object {

        fun create(
            window: Window,
            scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
        ): WidgetsBinding {

            val base = BindingBaseImpl()

            val rendererBinding = RendererBindingImpl(
                window,
                base,
                SchedulerBindingImpl(
                    window,
                    base,
                    ServicesBindingImpl(base)
                )
            )

            if (useWoodPecker) {
                GestureBinding2.initInstance(
                    window,
                    base,
                    rendererBinding,
                    scope
                )
            } else {
                GestureBinding.initInstance(
                    window,
                    base,
                    rendererBinding,
                    scope
                )
            }
            return WidgetsBindingImpl(
                window,
                base,
                rendererBinding
            )
        }

        var useWoodPecker = false
    }
}