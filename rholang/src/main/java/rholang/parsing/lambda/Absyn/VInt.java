package rholang.parsing.lambda.Absyn; // Java Package generated by the BNF Converter.

public class VInt extends Value {
  public final Integer integer_;
  public VInt(Integer p1) { integer_ = p1; }

  public <R,A> R accept(rholang.parsing.lambda.Absyn.Value.Visitor<R,A> v, A arg) { return v.visit(this, arg); }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof rholang.parsing.lambda.Absyn.VInt) {
      rholang.parsing.lambda.Absyn.VInt x = (rholang.parsing.lambda.Absyn.VInt)o;
      return this.integer_.equals(x.integer_);
    }
    return false;
  }

  public int hashCode() {
    return this.integer_.hashCode();
  }


}