package foo;

import static android.arch.lifecycle.Lifecycle.ON_STOP;

import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.OnLifecycleEvent;

class InheritanceOk2Base {
    @OnLifecycleEvent(ON_STOP)
    public void onStop(LifecycleOwner provider, int lastEvent) {
    }
}

class InheritanceOk2Derived extends InheritanceOk2Base {
    @OnLifecycleEvent(ON_STOP)
    public void onStop2(LifecycleOwner provider, int lastEvent) {
    }
}
