package qouteall.q_misc_util.my_util;

/**
 * Replacement of the removed net.minecraft.util.Tuple.
 */
public class Tuple<A, B> {
    private A a;
    private B b;
    
    public Tuple(A a, B b) {
        this.a = a;
        this.b = b;
    }
    
    public A getA() {
        return this.a;
    }
    
    public void setA(A a) {
        this.a = a;
    }
    
    public B getB() {
        return this.b;
    }
    
    public void setB(B b) {
        this.b = b;
    }
    
    @Override
    public String toString() {
        return "(" + a + ", " + b + ")";
    }
}
