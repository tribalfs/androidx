import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.TextView
import com.google.r4a.Composable
import com.google.r4a.adapters.setLayoutHeight
import com.google.r4a.adapters.setLayoutWidth

class MyClass {
    @Composable
    fun PasteInClass() {
        <TextView
            layoutWidth=WRAP_CONTENT
            layoutHeight=WRAP_CONTENT
            text="Hello World!" />
    }
}
