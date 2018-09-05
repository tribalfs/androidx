package androidx.ui.foundation.change_notifier

import androidx.annotation.CallSuper
import androidx.ui.VoidCallback

// import 'package:meta/meta.dart';
//
// import 'assertions.dart';
// import 'basic_types.dart';
// import 'diagnostics.dart';
// import 'observer_list.dart';

// / An object that maintains a list of listeners.
interface Listenable {
    companion object {
        // / Return a [Listenable] that triggers when any of the given [Listenable]s
        // / themselves trigger.
        // /
        // / The list must not be changed after this method has been called. Doing so
        // / will lead to memory leaks or exceptions.
        // /
        // / The list may contain nulls; they are ignored.
        fun merge(listenables: List<Listenable>) {
//            =_MergingListenable;
            TODO("not implemented")
        }
    }

    // / Register a closure to be called when the object notifies its listeners.
    fun addListener(listener: VoidCallback)

    // / Remove a previously registered closure from the list of closures that the
    // / object notifies.
    fun removeListener(listener: VoidCallback)
}

// / An interface for subclasses of [Listenable] that expose a [value].
// /
// / This interface is implemented by [ValueNotifier<T>] and [Animation<T>], and
// / allows other APIs to accept either of those implementations interchangeably.
interface ValueListenable<T> : Listenable {
    // / The current value of the object. When the value changes, the callbacks
    // / registered with [addListener] will be invoked.
    abstract var value: T
}

// / A class that can be extended or mixed in that provides a change notification
// / API using [VoidCallback] for notifications.
// /
// / [ChangeNotifier] is optimized for small numbers (one or two) of listeners.
// / It is O(N) for adding and removing listeners and O(N²) for dispatching
// / notifications (where N is the number of listeners).
// /
// / See also:
// /
// /  * [ValueNotifier], which is a [ChangeNotifier] that wraps a single value.
open class ChangeNotifier : Listenable {
    override fun addListener(listener: VoidCallback) {
        TODO("not implemented")
    }

    override fun removeListener(listener: VoidCallback) {
        TODO("not implemented")
    }

    //  ObserverList<VoidCallback> _listeners = new ObserverList<VoidCallback>();
//
//  bool _debugAssertNotDisposed() {
//    assert(() {
//      if (_listeners == null) {
//        throw new FlutterError(
//          'A $runtimeType was used after being disposed.\n'
//          'Once you have called dispose() on a $runtimeType, it can no longer be used.'
//        );
//      }
//      return true;
//    }());
//    return true;
//  }
//
//  /// Whether any listeners are currently registered.
//  ///
//  /// Clients should not depend on this value for their behavior, because having
//  /// one listener's logic change when another listener happens to start or stop
//  /// listening will lead to extremely hard-to-track bugs. Subclasses might use
//  /// this information to determine whether to do any work when there are no
//  /// listeners, however; for example, resuming a [Stream] when a listener is
//  /// added and pausing it when a listener is removed.
//  ///
//  /// Typically this is used by overriding [addListener], checking if
//  /// [hasListeners] is false before calling `super.addListener()`, and if so,
//  /// starting whatever work is needed to determine when to call
//  /// [notifyListeners]; and similarly, by overriding [removeListener], checking
//  /// if [hasListeners] is false after calling `super.removeListener()`, and if
//  /// so, stopping that same work.
//  @protected
//  bool get hasListeners {
//    assert(_debugAssertNotDisposed());
//    return _listeners.isNotEmpty;
//  }
//
//  /// Register a closure to be called when the object changes.
//  ///
//  /// This method must not be called after [dispose] has been called.
//  @override
//  void addListener(VoidCallback listener) {
//    assert(_debugAssertNotDisposed());
//    _listeners.add(listener);
//  }
//
//  /// Remove a previously registered closure from the list of closures that are
//  /// notified when the object changes.
//  ///
//  /// If the given listener is not registered, the call is ignored.
//  ///
//  /// This method must not be called after [dispose] has been called.
//  ///
//  /// If a listener had been added twice, and is removed once during an
//  /// iteration (i.e. in response to a notification), it will still be called
//  /// again. If, on the other hand, it is removed as many times as it was
//  /// registered, then it will no longer be called. This odd behavior is the
//  /// result of the [ChangeNotifier] not being able to determine which listener
//  /// is being removed, since they are identical, and therefore conservatively
//  /// still calling all the listeners when it knows that any are still
//  /// registered.
//  ///
//  /// This surprising behavior can be unexpectedly observed when registering a
//  /// listener on two separate objects which are both forwarding all
//  /// registrations to a common upstream object.
//  @override
//  void removeListener(VoidCallback listener) {
//    assert(_debugAssertNotDisposed());
//    _listeners.remove(listener);
//  }
//
//  /// Discards any resources used by the object. After this is called, the
//  /// object is not in a usable state and should be discarded (calls to
//  /// [addListener] and [removeListener] will throw after the object is
//  /// disposed).
//  ///
//  /// This method should only be called by the object's owner.
    @CallSuper
    open fun dispose() {
        TODO()
//    assert(_debugAssertNotDisposed());
//    _listeners = null;
    }
//
//  /// Call all the registered listeners.
//  ///
//  /// Call this method whenever the object changes, to notify any clients the
//  /// object may have. Listeners that are added during this iteration will not
//  /// be visited. Listeners that are removed during this iteration will not be
//  /// visited after they are removed.
//  ///
//  /// Exceptions thrown by listeners will be caught and reported using
//  /// [FlutterError.reportError].
//  ///
//  /// This method must not be called after [dispose] has been called.
//  ///
//  /// Surprising behavior can result when reentrantly removing a listener (i.e.
//  /// in response to a notification) that has been registered multiple times.
//  /// See the discussion at [removeListener].
//  @protected
//  void notifyListeners() {
//    assert(_debugAssertNotDisposed());
//    if (_listeners != null) {
//      final List<VoidCallback> localListeners = new List<VoidCallback>.from(_listeners);
//      for (VoidCallback listener in localListeners) {
//        try {
//          if (_listeners.contains(listener))
//            listener();
//        } catch (exception, stack) {
//          FlutterError.reportError(new FlutterErrorDetails(
//            exception: exception,
//            stack: stack,
//            library: 'foundation library',
//            context: 'while dispatching notifications for $runtimeType',
//            informationCollector: (StringBuffer information) {
//              information.writeln('The $runtimeType sending notification was:');
//              information.write('  $this');
//            }
//          ));
//        }
//      }
//    }
//  }
}

private class _MergingListenable : ChangeNotifier() {
//  _MergingListenable(this._children) {
//    for (Listenable child in _children)
//      child?.addListener(notifyListeners);
//  }
//
//  final List<Listenable> _children;
//
//  @override
//  void dispose() {
//    for (Listenable child in _children)
//      child?.removeListener(notifyListeners);
//    super.dispose();
//  }
//
//  @override
//  String toString() {
//    return 'Listenable.merge([${_children.join(", ")}])';
//  }
}

// / A [ChangeNotifier] that holds a single value.
// /
// / When [value] is replaced, this class notifies its listeners.
class ValueNotifier<T> : ChangeNotifier(), ValueListenable<T> {
    override var value: T
        get() = TODO("not implemented")
        set(value) = TODO("not implemented")
//  /// Creates a [ChangeNotifier] that wraps this value.
//  ValueNotifier(this._value);
//
//  /// The current value stored in this notifier.
//  ///
//  /// When the value is replaced, this class notifies its listeners.
//  @override
//  T get value => _value;
//  T _value;
//  set value(T newValue) {
//    if (_value == newValue)
//      return;
//    _value = newValue;
//    notifyListeners();
//  }
//
//  @override
//  String toString() => '${describeIdentity(this)}($value)';
}
