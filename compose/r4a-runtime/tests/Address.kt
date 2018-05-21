import com.google.r4a.frames.AbstractRecord
import com.google.r4a.frames.Framed
import com.google.r4a.frames.readable
import com.google.r4a.frames.writable
import com.google.r4a.frames.Record

class Address(street: String, city: String): Framed {
    var street: String
        get() = next.readable().street
        set(value) { next.writable(this).street = value }

    var city: String
        get() = next.readable().city
        set(value) { next.writable(this).city = value }


    private var next: AddressRecord

    init {
        next = AddressRecord()
        next.street = street
        next.city = city
    }

    override fun prepend(value: Record) {
        value.next = next
        next = value as AddressRecord
    }

    override val first: AddressRecord get() = next
}

class AddressRecord : AbstractRecord() {
    var street: String = ""
    var city: String = ""

    override fun create(): Record = AddressRecord()
    override fun assign(value: Record) {
        val other = value as AddressRecord
        street = other.street
        city = other.city
    }
}
