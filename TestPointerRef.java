import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.Pointer;

public class TestPointerRef {
    public static void main(String[] args) {
        PointerByReference ref = new PointerByReference();
        System.out.println("Initial value: " + ref.getValue());
        ref.setValue(null);
        System.out.println("After setting null: " + ref.getValue());
    }
}
