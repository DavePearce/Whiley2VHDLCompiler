package wyvc.lang;


import wyvc.lang.TypedValue.Port;
import wyvc.lang.TypedValue.Port.Mode;
import wyvc.lang.TypedValue.PortException;
import wyvc.lang.Type.Signed;
import wyvc.lang.Type.TypeException;
import wyvc.lang.Type.Unsigned;
import wyvc.lang.Type.VectorType;


public interface Expression extends LexicalElement {
	public Type getType();
	public int getPrecedence();

	public final class TypesMismatchException extends TypeException {
		private static final long serialVersionUID = -2926759938972785709L;
		private final Type expected;

		public TypesMismatchException(Class<?> element, Type expected, Type given) {
			super(element, given);
			this.expected = expected;
		}

		@Override
		protected void typeExceptionDetails() {
			System.err.println(expected.toString() + " expected");
		}

	}


	public static final class Precedence {
		public static final int LOGICAL_OP 	= 0;
		public static final int COMPARISON 	= LOGICAL_OP 	+ 1;
		public static final int SHIFT_OP 	= COMPARISON	+ 1;
		public static final int ADDITIVE_OP = SHIFT_OP		+ 1;
		public static final int UNARY_SIGN 	= ADDITIVE_OP	+ 1;
		public static final int MULTIPL_OP 	= UNARY_SIGN	+ 1;
		public static final int UNARY_NOT 	= MULTIPL_OP	+ 1;
		public static final int VAR_ACCESS 	= UNARY_NOT		+ 1;

	}

	public static abstract class BinaryOperation extends TypedElement implements Expression {
		private final String op;
		public final Expression arg1, arg2;
		public final int precedence;

		protected BinaryOperation(Expression arg1, String op, Expression arg2, int precedence, Type type){
			super(type);
			this.op = op;
			this.arg1 = arg1;
			this.arg2 = arg2;
			this.precedence = precedence;
		}

		@Override
		public final void addTokens(Token t) {
			int p = getPrecedence();
			int p1 = arg1.getPrecedence();
			int p2 = arg2.getPrecedence();
			if (p > p1)
				t.n("(").n(arg1).n(")");
			else
				t.n(arg1);
			t.n(" "+op+" ");
			if (p2 > p)
				t.n(arg2);
			else
				t.n("(").n(arg2).n(")");
		}

		@Override
		public int getPrecedence() {
			return precedence;
		}
	}

	public static abstract class LogicalBinaryOperation extends BinaryOperation {
		public LogicalBinaryOperation(Expression arg1, String op, Expression arg2) throws TypesMismatchException {
				super(arg1, op, arg2, Precedence.LOGICAL_OP, getType(arg1.getType(), arg2.getType()));
			}

		private static final Type getType(Type t1, Type t2) throws TypesMismatchException{
			if (t1.equals(t2))
				return t1;
			if (t1 instanceof VectorType && t2 instanceof VectorType) {
				VectorType vt1 = (VectorType) t1;
				VectorType vt2 = (VectorType) t2;
				if (vt1.isSameVectorType(vt2) && !(vt1.isAscendant()^vt2.isAscendant()))
					return vt1.isAscendant() ? vt1.cloneType(0, Math.max(vt1.lenght(), vt2.lenght())-1)
					                         : vt1.cloneType(Math.max(vt1.lenght(), vt2.lenght())-1, 0);
				throw new TypesMismatchException(LogicalBinaryOperation.class, vt1, vt2);
			}
			throw new TypesMismatchException(LogicalBinaryOperation.class, t1, t2);
		}
	}

	public static final class And extends LogicalBinaryOperation {
		public And(Expression arg1, Expression arg2) throws TypesMismatchException {
			super(arg1, "and", arg2);
		}
	}

	public static final class Nand extends LogicalBinaryOperation {
		public Nand(Expression arg1, Expression arg2) throws TypesMismatchException {
			super(arg1, "nand", arg2);
		}
	}

	public static final class Or extends LogicalBinaryOperation {
		public Or(Expression arg1, Expression arg2) throws TypesMismatchException {
			super(arg1, "or", arg2);
		}
	}

	public static final class Nor extends LogicalBinaryOperation {
		public Nor(Expression arg1, Expression arg2) throws TypesMismatchException {
			super(arg1, "nor", arg2);
		}
	}

	public static final class Xor extends LogicalBinaryOperation {
		public Xor(Expression arg1, Expression arg2) throws TypesMismatchException {
			super(arg1, "xor", arg2);
		}
	}

	public static final class Xnor extends LogicalBinaryOperation {
		public Xnor(Expression arg1, Expression arg2) throws TypesMismatchException {
			super(arg1, "xnor", arg2);
		}
	}





	public static abstract class AdditiveBinaryOperation extends BinaryOperation {
		public AdditiveBinaryOperation(Expression arg1, String op, Expression arg2) throws TypesMismatchException {
				super(arg1, op, arg2, Precedence.ADDITIVE_OP, getType(arg1.getType(), arg2.getType()));
			}

		private static final Type getType(Type t1, Type t2) throws TypesMismatchException{
			if (t1 instanceof Unsigned && t2 instanceof Unsigned &&
					!(((Unsigned) t1).isAscendant() ^ ((Unsigned) t2).isAscendant())) {
				Unsigned vt1 = (Unsigned) t1;
				Unsigned vt2 = (Unsigned) t2;
				return vt1.isAscendant() ? new Unsigned(0, Math.max(vt1.lenght(), vt2.lenght())-1)
				                         : new Unsigned(Math.max(vt1.lenght(), vt2.lenght())-1, 0);
			}
			if (t1 instanceof Signed && t2 instanceof Signed &&
					!(((Signed) t1).isAscendant() ^ ((Signed) t2).isAscendant())) {
				Signed vt1 = (Signed) t1;
				Signed vt2 = (Signed) t2;
				return vt1.isAscendant() ? new Signed(0, Math.max(vt1.lenght(), vt2.lenght())-1)
				                         : new Signed(Math.max(vt1.lenght(), vt2.lenght())-1, 0);
			}
			throw new TypesMismatchException(LogicalBinaryOperation.class, t1, t2);
		}
	}

	public static final class Add extends AdditiveBinaryOperation {
		public Add(Expression arg1, Expression arg2) throws TypesMismatchException {
			super(arg1, "+", arg2);
		}
	}

	public static final class Sub extends AdditiveBinaryOperation {
		public Sub(Expression arg1, Expression arg2) throws TypesMismatchException {
			super(arg1, "-", arg2);
		}
	}


/*
	public static abstract class MultiplicativeBinaryOperation extends BinaryOperation {
		public MultiplicativeBinaryOperation(Expression arg1, String op, Expression arg2, Type type) throws TypesMismatchException {
			super(arg1, op, arg2, Precedence.MULTIPL_OP, type);
		}

		public static final VectorType mulType(VectorType t1, VectorType t2) throws TypesMismatchException {
			if (t1.isAscendant() ^ t2.isAscendant())
				throw new TypesMismatchException(MultiplicativeBinaryOperation.class, t2.cloneType(t2.end, t2.start), t2);
			int l = t1.lenght()+t2.lenght()-1;
			if (t1 instanceof Unsigned && t2 instanceof Unsigned)
				return t1.isAscendant() ? new Unsigned(0, l) : new Unsigned(l, 0);
			if (t1 instanceof Signed && t2 instanceof Signed)
				return t1.isAscendant() ? new Signed(0, l) : new Signed(l, 0);
			throw new TypesMismatchException(MultiplicativeBinaryOperation.class, t1.cloneType(t2.start, t2.end), t2);
		}
	}

	public static final class Mul extends MultiplicativeBinaryOperation {
		public Mul(Expression arg1, Expression arg2) throws TypesMismatchException {
			super(arg1, "*", arg2, mulType(arg1.getType(), arg2.getType()));
		}
	}

	public static final class Div extends MultiplicativeBinaryOperation {

	}

	public static final class Mod extends MultiplicativeBinaryOperation {

	}

	public static final class Rem extends MultiplicativeBinaryOperation {

	}
*/

	public static class Access extends TypedElement implements Expression {
		public final TypedValue value;

		public Access(TypedValue value) throws PortException {
			super(value.type);
			this.value = value;
			if (value instanceof Port && ((Port) value).mode == Mode.OUT)
				throw new PortException(Access.class, (Port) value);
		}

		@Override
		public int getPrecedence() {
			return Precedence.VAR_ACCESS;
		}

		@Override
		public void addTokens(Token t) {
			t.n(value.ident);
			// TODO Auto-generated method stub

		}

	}

	public static class Value extends TypedElement implements Expression {
		public final String value;

		public Value(Type type, String value) {
			super(type);
			this.value = value;
		}

		@Override
		public void addTokens(Token t) {
			t.n(value);
		}

		@Override
		public int getPrecedence() {
			return Precedence.VAR_ACCESS;
		}


	}

}
