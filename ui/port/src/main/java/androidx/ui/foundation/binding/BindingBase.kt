package androidx.ui.foundation.binding

import androidx.annotation.CallSuper
import androidx.ui.developer.timeline.Timeline
import androidx.ui.foundation.assertions.FlutterError
import androidx.ui.runtimeType
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking

// / Signature for service extensions.
// /
// / The returned map must not contain the keys "type" or "method", as
// / they will be replaced before the value is sent to the client. The
// / "type" key will be set to the string `_extensionType` to indicate
// / that this is a return value from a service extension, and the
// / "method" key will be set to the full name of the method.
typealias ServiceExtensionCallback = (Map<String, String>) -> Deferred<Map<String, Any?>>

interface BindingBase {

    fun reassembleApplication(): Deferred<Unit>

    fun performReassemble(): Deferred<Unit>

    fun lockEvents(callback: () -> Deferred<Unit>): Deferred<Unit>

    // / Registers a service extension method with the given name (full
    // / name "ext.flutter.name"), which takes no arguments and returns
    // / no value.
    // /
    // / Calls the `callback` callback when the service extension is called.
    fun registerSignalServiceExtension(
        name: String,
        callback: Deferred<Unit>
    )

    // / Registers a service extension method with the given name (full
    // / name "ext.flutter.name"). The given callback is called when the
    // / extension method is called. The callback must return a [Future]
    // / that either eventually completes to a return value in the form
    // / of a name/value map where the values can all be converted to
    // / JSON using `json.encode()` (see [JsonEncoder]), or fails. In case of failure, the
    // / failure is reported to the remote caller and is dumped to the
    // / logs.
    // /
    // / The returned map will be mutated.
    fun registerServiceExtension(
        name: String,
        callback: ServiceExtensionCallback
    )
}

// / Base class for mixins that provide singleton services (also known as
// / "bindings").
// /
// / To use this class in a mixin, inherit from it and implement
// / [initInstances()]. The mixin is guaranteed to only be constructed once in
// / the lifetime of the app (more precisely, it will assert if constructed twice
// / in checked mode).
// /
// / The top-most layer used to write the application will have a concrete class
// / that inherits from [BindingBase] and uses all the various [BindingBase]
// / mixins (such as [ServicesBinding]). For example, the Widgets library in
// / Flutter introduces a binding called [WidgetsFlutterBinding]. The relevant
// / library defines how to create the binding. It could be implied (for example,
// / [WidgetsFlutterBinding] is automatically started from [runApp]), or the
// / application might be required to explicitly call the constructor.
object BindingBaseImpl : BindingBase {

    // / Default abstract constructor for bindings.
    // /
    // / First calls [initInstances] to have bindings initialize their
    // / instance pointers and other state, then calls
    // / [initServiceExtensions] to have bindings initialize their
    // / observatory service extensions, if any.
    init {
        Timeline.startSync("BindingBase initialization")

//        developer.postEvent('Flutter.FrameworkInitialization', <String, String>{});

        Timeline.finishSync()
    }

    // / Called when the binding is initialized, to register service
    // / extensions.
    // /
    // / Bindings that want to expose service extensions should overload
    // / this method to register them using calls to
    // / [registerSignalServiceExtension],
    // / [registerBoolServiceExtension],
    // / [registerNumericServiceExtension], and
    // / [registerServiceExtension] (in increasing order of complexity).
    // /
    // / Implementations of this method must call their superclass
    // / implementation.
    // /
    // / Service extensions are only exposed when the observatory is
    // / included in the build, which should only happen in checked mode
    // / and in profile mode.
    // /
    // / See also:
    // /
    // /  * <https://github.com/dart-lang/sdk/blob/master/runtime/vm/service/service.md#rpcs-requests-and-responses>
    init { // was initServiceExtensions
        // TODO(migration/popam): Implement this
//        registerSignalServiceExtension(
//                name: 'reassemble',
//        callback: reassembleApplication,
//        );
//        registerSignalServiceExtension(
//                name: 'exit',
//        callback: _exitApplication,
//        );
//        registerSignalServiceExtension(
//                name: 'frameworkPresent',
//        callback: () => new Future<Null>.value(),
//        );
//        assert(() {
//            registerServiceExtension(
//                    name: 'platformOverride',
//            callback: (Map<String, String> parameters) async {
//            if (parameters.containsKey('value')) {
//                switch (parameters['value']) {
//                    case 'android':
//                    debugDefaultTargetPlatformOverride = TargetPlatform.android;
//                    break;
//                    case 'iOS':
//                    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
//                    break;
//                    case 'fuchsia':
//                    debugDefaultTargetPlatformOverride = TargetPlatform.fuchsia;
//                    break;
//                    case 'default':
//                    default:
//                    debugDefaultTargetPlatformOverride = null;
//                }
//                await reassembleApplication();
//            }
//            return <String, dynamic>{
//                'value': defaultTargetPlatform
//                .toString()
//                    .substring('$TargetPlatform.'.length),
//            };
//        }
//            );
//            return true;
//        }());
//        assert(() { _debugServiceExtensionsRegistered = true; return true; }());
    }

    // / Whether [lockEvents] is currently locking events.
    // /
    // / Binding subclasses that fire events should check this first, and if it is
    // / set, queue events instead of firing them.
    // /
    // / Events should be flushed when [unlocked] is called.
    val locked
        get() = _lockCount > 0
    @Volatile // TODO(Migration/Andrey): Ask Flutter team how it works in Dart w/o volatile!
    private var _lockCount = 0

    // / Locks the dispatching of asynchronous events and callbacks until the
    // / callback's future completes.
    // /
    // / This causes input lag and should therefore be avoided when possible. It is
    // / primarily intended for use during non-user-interactive time such as to
    // / allow [reassembleApplication] to block input while it walks the tree
    // / (which it partially does asynchronously).
    // /
    // / The [Future] returned by the `callback` argument is returned by [lockEvents].
    override fun lockEvents(callback: () -> Deferred<Unit>): Deferred<Unit> {
        Timeline.startSync("Lock events")

        assert(callback != null)
        _lockCount += 1
        val future = callback()
        assert(future != null, { "The lockEvents() callback returned null;" +
                "it should return a Future<Null> that completes when the lock is to expire." })
        future.invokeOnCompletion {
            _lockCount -= 1
            if (!locked) {
                Timeline.finishSync()
                unlocked()
            }
        }
        return future
    }

    // / Called by [lockEvents] when events get unlocked.
    // /
    // / This should flush any events that were queued while [locked] was true.
    @CallSuper
    fun unlocked() {
        assert(!locked)
    }

    // / Cause the entire application to redraw, e.g. after a hot reload.
    // /
    // / This is used by development tools when the application code has changed,
    // / to cause the application to pick up any changed code. It can be triggered
    // / manually by sending the `ext.flutter.reassemble` service extension signal.
    // /
    // / This method is very computationally expensive and should not be used in
    // / production code. There is never a valid reason to cause the entire
    // / application to repaint in production. All aspects of the Flutter framework
    // / know how to redraw when necessary. It is only necessary in development
    // / when the code is literally changed on the fly (e.g. in hot reload) or when
    // / debug flags are being toggled.
    // /
    // / While this method runs, events are locked (e.g. pointer events are not
    // / dispatched).
    // /
    // / Subclasses (binding classes) should override [performReassemble] to react
    // / to this method being called. This method itself should not be overridden.
    override fun reassembleApplication(): Deferred<Unit> {
        return lockEvents(::performReassemble)
    }

    // / This method is called by [reassembleApplication] to actually cause the
    // / application to reassemble, e.g. after a hot reload.
    // /
    // / Bindings are expected to use this method to reregister anything that uses
    // / closures, so that they do not keep pointing to old code, and to flush any
    // / caches of previously computed values, in case the new code would compute
    // / them differently. For example, the rendering layer triggers the entire
    // / application to repaint when this is called.
    // /
    // / Do not call this method directly. Instead, use [reassembleApplication].
    @CallSuper
    override fun performReassemble(): Deferred<Unit> {
        FlutterError.resetErrorCount()
        return async { }
    }

    // / Registers a service extension method with the given name (full
    // / name "ext.flutter.name"), which takes no arguments and returns
    // / no value.
    // /
    // / Calls the `callback` callback when the service extension is called.
    override fun registerSignalServiceExtension(
        name: String,
        callback: Deferred<Unit>
    ) {
        assert(name != null)
        assert(callback != null)
        registerServiceExtension(
                name,
                { parameters: Map<String, String> ->
                    async {
                        runBlocking { callback.await(); }
                        mapOf<String, Any?>()
                    }
                }
        )
    }

    // / Registers a service extension method with the given name (full
    // / name "ext.flutter.name"), which takes a single argument
    // / "enabled" which can have the value "true" or the value "false"
    // / or can be omitted to read the current value. (Any value other
    // / than "true" is considered equivalent to "false". Other arguments
    // / are ignored.)
    // /
    // / Calls the `getter` callback to obtain the value when
    // / responding to the service extension method being called.
    // /
    // / Calls the `setter` callback with the new value when the
    // / service extension method is called with a new value.
//    @protected
//    void registerBoolServiceExtension({
//        @required String name,
//        @required AsyncValueGetter<bool> getter,
//        @required AsyncValueSetter<bool> setter
//    }) {
//        assert(name != null);
//        assert(getter != null);
//        assert(setter != null);
//        registerServiceExtension(
//                name: name,
//                callback: (Map<String, String> parameters) async {
//            if (parameters.containsKey('enabled'))
//                await setter(parameters['enabled'] == 'true');
//            return <String, dynamic>{ 'enabled': await getter() ? 'true' : 'false' };
//        }
//        );
//    }

    // / Registers a service extension method with the given name (full
    // / name "ext.flutter.name"), which takes a single argument with the
    // / same name as the method which, if present, must have a value
    // / that can be parsed by [double.parse], and can be omitted to read
    // / the current value. (Other arguments are ignored.)
    // /
    // / Calls the `getter` callback to obtain the value when
    // / responding to the service extension method being called.
    // /
    // / Calls the `setter` callback with the new value when the
    // / service extension method is called with a new value.
// //    @protected
//    fun registerNumericServiceExtension(
//        name: String,
//        getter : AsyncValueGetter<Double>,
//        setter : AsyncValueSetter<Double>
//    ) {
//        assert(name != null);
//        assert(getter != null);
//        assert(setter != null);
//        registerServiceExtension(
//                name: name,
//                callback: (Map<String, String> parameters) async {
//            if (parameters.containsKey(name))
//                await setter(double.parse(parameters[name]));
//            return <String, dynamic>{ name: (await getter()).toString() };
//        }
//        );
//    }
//
//    /// Registers a service extension method with the given name (full name
//    /// "ext.flutter.name"), which optionally takes a single argument with the
//    /// name "value". If the argument is omitted, the value is to be read,
//    /// otherwise it is to be set. Returns the current value.
//    ///
//    /// Calls the `getter` callback to obtain the value when
//    /// responding to the service extension method being called.
//    ///
//    /// Calls the `setter` callback with the new value when the
//    /// service extension method is called with a new value.
//    @protected
//    void registerStringServiceExtension({
//        @required String name,
//        @required AsyncValueGetter<String> getter,
//        @required AsyncValueSetter<String> setter
//    }) {
//        assert(name != null);
//        assert(getter != null);
//        assert(setter != null);
//        registerServiceExtension(
//                name: name,
//                callback: (Map<String, String> parameters) async {
//            if (parameters.containsKey('value'))
//                await setter(parameters['value']);
//            return <String, dynamic>{ 'value': await getter() };
//        }
//        );
//    }

    // / Registers a service extension method with the given name (full
    // / name "ext.flutter.name"). The given callback is called when the
    // / extension method is called. The callback must return a [Future]
    // / that either eventually completes to a return value in the form
    // / of a name/value map where the values can all be converted to
    // / JSON using `json.encode()` (see [JsonEncoder]), or fails. In case of failure, the
    // / failure is reported to the remote caller and is dumped to the
    // / logs.
    // /
    // / The returned map will be mutated.
    override fun registerServiceExtension(
        name: String,
        callback: ServiceExtensionCallback
    ) {
        assert(name != null)
        assert(callback != null)
        val methodName = "ext.flutter.$name"
        // TODO(migration/popam): translate this
//        developer.registerExtension(methodName, (String method, Map<String, String> parameters) async {
//            assert(method == methodName);
//            assert(() {
//                if (debugInstrumentationEnabled)
//                    debugPrint('service extension method received: $method($parameters)');
//                return true;
//            }());
//
//            // VM service extensions are handled as "out of band" messages by the VM,
//            // which means they are handled at various times, generally ASAP.
//            // Notably, this includes being handled in the middle of microtask loops.
//            // While this makes sense for some service extensions (e.g. "dump current
//            // stack trace", which explicitly doesn't want to wait for a loop to
//            // complete), Flutter extensions need not be handled with such high
//            // priority. Further, handling them with such high priority exposes us to
//            // the possibility that they're handled in the middle of a frame, which
//            // breaks many assertions. As such, we ensure they we run the callbacks
//            // on the outer event loop here.
//            await debugInstrumentAction<void>('Wait for outer event loop', () {
//                return new Future<void>.delayed(Duration.zero);
//            });
//
//            dynamic caughtException;
//            StackTrace caughtStack;
//            Map<String, dynamic> result;
//            try {
//                result = await callback(parameters);
//            } catch (exception, stack) {
//            caughtException = exception;
//            caughtStack = stack;
//        }
//            if (caughtException == null) {
//                result['type'] = '_extensionType';
//                result['method'] = method;
//                return new developer.ServiceExtensionResponse.result(json.encode(result));
//            } else {
//                FlutterError.reportError(new FlutterErrorDetails(
//                        exception: caughtException,
//                        stack: caughtStack,
//                        context: 'during a service extension callback for "$method"'
//                ));
//                return new developer.ServiceExtensionResponse.error(
//                        developer.ServiceExtensionResponse.extensionError,
//                json.encode(<String, String>{
//                    'exception': caughtException.toString(),
//                    'stack': caughtStack.toString(),
//                    'method': method,
//                })
//                );
//            }
//        });
    }

    override fun toString(): String {
        return "<${runtimeType()}"
    }
}

// TODO(migration/popam)
// / Terminate the Flutter application.
// Future<Null> _exitApplication() async {
//    exit(0);
// }
