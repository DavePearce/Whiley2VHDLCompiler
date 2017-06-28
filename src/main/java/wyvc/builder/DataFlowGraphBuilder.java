package wyvc.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import wyil.lang.Bytecode;
import wyil.lang.Bytecode.OperatorKind;
import wyil.lang.Constant;
import wyil.lang.SyntaxTree;
import wyil.lang.SyntaxTree.Location;
import wyil.lang.WyilFile;
import wyil.lang.WyilFile.FunctionOrMethod;
import wyvc.builder.DataFlowGraph;
import wyvc.builder.CompilerLogger.CompilerError;
import wyvc.builder.CompilerLogger.CompilerException;
import wyvc.builder.CompilerLogger.CompilerNotice;
import wyvc.builder.CompilerLogger.LoggedBuilder;
import wyvc.builder.CompilerLogger.UnsupportedCompilerError;
import wyvc.builder.DataFlowGraph.BinaryOperation;
import wyvc.builder.DataFlowGraph.DataNode;
import wyvc.builder.DataFlowGraph.FuncCallNode;
import wyvc.builder.DataFlowGraph.HalfArrow;
import wyvc.builder.DataFlowGraph.UnaryOperation;
import wyvc.builder.TypeCompiler.AccessibleTypeTree;
import wyvc.builder.TypeCompiler.BooleanTypeLeaf;
import wyvc.builder.TypeCompiler.TypeLeaf;
import wyvc.builder.TypeCompiler.TypeOption;
import wyvc.builder.TypeCompiler.TypeRecordUnion;
import wyvc.builder.TypeCompiler.TypeSimpleRecord;
import wyvc.builder.TypeCompiler.TypeSimpleUnion;
import wyvc.builder.TypeCompiler.TypeTree;
import wyvc.builder.TypeCompiler.TypeUnion;
import wyvc.builder.LexicalElementTree;
import wyvc.lang.Type;
import wyvc.utils.FunctionalInterfaces.BiFunction;
import wyvc.utils.FunctionalInterfaces.BiFunction_;
import wyvc.utils.Generators;
import wyvc.utils.Pair;
import wyvc.utils.Utils;
import wyvc.utils.Generators.Generator_;
import wyvc.utils.Generators.Generator;
import wyvc.utils.Generators.PairGenerator;

public final class DataFlowGraphBuilder extends LexicalElementTree {
	private final TypeCompiler typeCompiler;


	public DataFlowGraphBuilder(CompilerLogger logger, TypeCompiler typeCompiler) {
		super(logger);
		this.typeCompiler = typeCompiler;
	}


	public static class NestedReturnCompilerNotice extends CompilerNotice {
		@Override
		public String info() {
			return "Nested returns can result in a large hardware configuration";
		}
	}

	public static class WyilUnsupportedCompilerError extends UnsupportedCompilerError {
		private final Location<?> location;
		public WyilUnsupportedCompilerError(Location<?> location) {
			this.location = location;
		}

		@Override
		public String info() {
			return location + " is currently unsupported";
		}

		public static CompilerException exception(Location<?> location) {
			return new CompilerException(new WyilUnsupportedCompilerError(location));
		}
	}

	public static class UnsupportedAssignmentCompilerError extends UnsupportedCompilerError {
		private final Location<?> location;
		private final AccessibleVertexTree<?> value;
		public UnsupportedAssignmentCompilerError(Location<?> location, AccessibleVertexTree<?> value) {
			this.location = location;
			this.value = value;
		}

		@Override
		public String info() {
			return "The assigment to <"+location.toString() + "> is unsupported with a value\n"+value.toString("  ")+
					"\nof type\n"+value.getType().toString("  ");
		}

		public static CompilerException exception(Location<?> location, AccessibleVertexTree<?> value) {
			return new CompilerException(new UnsupportedAssignmentCompilerError(location, value));
		}
	}


	public static class UnsupportedTreeNodeCompilerError extends CompilerError {
		private final Tree<?> node;

		public UnsupportedTreeNodeCompilerError(Tree<?> node) {
			this.node = node;
		}

		@Override
		public String info() {
			return "The conversion of the node "+node+" is unsupported";
		}

		public static CompilerException exception(Tree<?> node) {
			return new CompilerException(new UnsupportedTreeNodeCompilerError(node));
		}
	}


	public static class UnrelatedTypeCompilerError extends CompilerError {
		private final AccessibleVertexTree<?> value;
		private final AccessibleTypeTree type;

		public UnrelatedTypeCompilerError(AccessibleTypeTree type, AccessibleVertexTree<?> value) {
			this.value = value;
			this.type = type;
		}

		@Override
		public String info() {
			return "The value <"+value+"> of type\n"+value.getType().toString("  ")+"\ncan't be interpreted as part of the type \n"+type.toString("  ");
		}

		public static CompilerException exception(AccessibleTypeTree type, AccessibleVertexTree<?> value) {
			return new CompilerException(new UnrelatedTypeCompilerError(type, value));
		}
	}

	public static class UnsupportedAliasCompilerError extends CompilerError {
		private final AccessibleVertexTree<?> value;
		private final AccessibleTypeTree type;

		public UnsupportedAliasCompilerError(AccessibleTypeTree type, AccessibleVertexTree<?> value) {
			this.value = value;
			this.type = type;
		}

		@Override
		public String info() {
			return "Unsupported alias of value <"+value+"> of type\n"+value.getType().toString("  ")+"\nto the type\n"+type.toString("  ");
		}

		public static CompilerException exception(AccessibleTypeTree type, AccessibleVertexTree<?> value) {
			return new CompilerException(new UnsupportedAliasCompilerError(type, value));
		}
	}

	public static class UnsupportedOperatorCompilerError extends CompilerError {
		private final Location<Bytecode.Operator> op;

		public UnsupportedOperatorCompilerError(Location<Bytecode.Operator> op) {
			this.op = op;
		}

		@Override
		public String info() {
			return "Unsupported operator <"+op+"> of kind "+op.getBytecode().kind().name();
		}

		public static CompilerException exception(Location<Bytecode.Operator> op) {
			return new CompilerException(new UnsupportedOperatorCompilerError(op));
		}
	}


	public static class UnsupportedFlowTypingCompilerError extends CompilerError {
		private final AccessibleVertexTree<?> value;
		private final AccessibleTypeTree type;

		public UnsupportedFlowTypingCompilerError(AccessibleTypeTree type, AccessibleVertexTree<?> value) {
			this.value = value;
			this.type = type;
		}

		@Override
		public String info() {
			return "Unsupported type test of value <"+value+"> of type\n"+value.getType().toString("  ")+"\nto the type\n"+type.toString("  ");
		}

		public static CompilerException exception(AccessibleTypeTree type, AccessibleVertexTree<?> value) {
			return new CompilerException(new UnsupportedFlowTypingCompilerError(type, value));
		}

	}



	public static class IncompatibleTypeCompilerNotice extends CompilerNotice {
		private final AccessibleVertexTree<?> value;
		private final AccessibleTypeTree type;

		public IncompatibleTypeCompilerNotice(AccessibleVertexTree<?> value, AccessibleTypeTree type) {
			this.value = value;
			this.type = type;
		}

		@Override
		public String info() {
			return "The value <"+value+"> of type\n"+value.getType().toString("  ")+"\ncan never be flow-typed to the type\n"+type.toString("  ");
		}
	}






	private static interface VertexTree extends Tree<HalfArrow<?>> {

	}

	private static interface AccessibleVertexTree<T extends AccessibleTypeTree> extends VertexTree {
		T getType();
	}

	private abstract class VertexLeaf<T extends TypeLeaf> extends Leaf<HalfArrow<?>> implements AccessibleVertexTree<T> {


		public <U extends DataNode> VertexLeaf(U value) {
			super(new HalfArrow<>(value));
		}
		public  <U extends DataNode> VertexLeaf(U value, String ident) {
			super(new HalfArrow<>(value, ident));
		}

//		public <U extends DataNode> VertexLeaf(U decl, String ident) {
//			super(new HalfArrow<>(decl, ident));
//			type = typeCompiler.new TypeLeaf(decl.type);
//		}

		public abstract T getType(); // TODO hum ?
//		public final Type getType() {
//			return type;
//		}
	}

	private final class GeneralVertexLeaf extends VertexLeaf<TypeLeaf> {
		final TypeLeaf type;

		public <U extends DataNode> GeneralVertexLeaf(U value) {
			super(value);
			type = typeCompiler.new TypeLeaf(value.type);
		}
		public <U extends DataNode> GeneralVertexLeaf(U value, String ident) {
			super(value, ident);
			type = typeCompiler.new TypeLeaf(value.type);
		}

		@Override
		public TypeLeaf getType() {
			return type;
		}

	}

	private final class BooleanVertexLeaf extends VertexLeaf<BooleanTypeLeaf> {
		final BooleanTypeLeaf type = typeCompiler.new BooleanTypeLeaf();
		public <U extends DataNode> BooleanVertexLeaf(U value) {
			super(value); // Check value boolean ?
		}
		public <U extends DataNode> BooleanVertexLeaf(U value, String ident) {
			super(value, ident);
		}

		@Override
		public BooleanTypeLeaf getType() {
			return type;
		}
	}

	private static interface VertexNode<A extends VertexTree> extends VertexTree, Node<A,HalfArrow<?>> {

	}

	private class VertexRecord<T extends VertexTree> extends NamedNode<T,HalfArrow<?>> implements VertexNode<T> {
		public <R extends T> VertexRecord(Generator<Pair<String, R>> fields) {
			super(fields);
		}

		public <R extends T, E extends Exception> VertexRecord(Generator_<Pair<String, R>, E> fields) throws E {
			super(fields);
		}
	}

	private final class VertexSimpleRecord extends VertexRecord<AccessibleVertexTree<?>> implements AccessibleVertexTree<TypeSimpleRecord> {
		final TypeSimpleRecord type;

		public <R extends AccessibleVertexTree<?>> VertexSimpleRecord(Generator<Pair<String, R>> fields) {
			super(fields);
			type = typeCompiler.new TypeSimpleRecord(getFields().mapSecond(AccessibleVertexTree::getType));
		}

		public <R extends AccessibleVertexTree<?>, E extends Exception> VertexSimpleRecord(Generator_<Pair<String, R>, E> fields) throws E {
			super(fields);
			type = typeCompiler.new TypeSimpleRecord(getFields().mapSecond(AccessibleVertexTree::getType));
		}

		@Override
		public TypeSimpleRecord getType() {
			return type;
		}
	}


	private final class VertexOption<U extends VertexTree> extends BinaryNode<VertexTree, BooleanVertexLeaf, U,HalfArrow<?>>
	implements VertexNode<VertexTree> {

		public VertexOption(BooleanVertexLeaf has, U val) {
			super(has,val);
		}

		@Override
		public String getFirstLabel() {
			return "has";
		}
		@Override
		public String getSecondLabel() {
			return "val";
		}
	}

	private final class VertexSimpleUnion
	extends UnnamedNode<VertexOption<VertexLeaf<?>>,HalfArrow<?>>
	implements VertexNode<VertexOption<VertexLeaf<?>>>, AccessibleVertexTree<TypeSimpleUnion> {
		final TypeSimpleUnion type;

		public VertexSimpleUnion(Generator<VertexOption<VertexLeaf<?>>> options) {
			super(options);
			type = typeCompiler.new TypeSimpleUnion(getOptions().map(o -> typeCompiler.new TypeOption<>(o.getSecondOperand().getType())));
		}

		public <E extends Exception> VertexSimpleUnion(Generator_<VertexOption<VertexLeaf<?>>, E> options) throws E {
			super(options);
			type = typeCompiler.new TypeSimpleUnion(getOptions().map(o -> typeCompiler.new TypeOption<>(o.getSecondOperand().getType())));
		}

		@Override
		public TypeSimpleUnion getType() {
			return type;
		}
	}

	private class VertexRecordUnion
	extends BinaryNode<VertexTree,VertexSimpleRecord,VertexRecord<VertexOption<AccessibleVertexTree<?>>>,HalfArrow<?>>
	implements VertexNode<VertexTree>, AccessibleVertexTree<TypeRecordUnion> {
		final TypeRecordUnion type;
//		public VertexRecordUnion(VertexSimpleRecord shared, VertexRecord<TypeOption, VertexOption> specific, TypeRecordUnion type) {
//			super(shared, specific);
//			this.type = type;
//		}

		public <A extends AccessibleVertexTree<?>> VertexRecordUnion(
				Generator<Pair<String,A>> shared,
				Generator<Pair<String, VertexOption<AccessibleVertexTree<?>>>> specific) {
			super(new VertexSimpleRecord(shared), new VertexRecord<>(specific));
			type = typeCompiler.new TypeRecordUnion(getFirstOperand().getType(), typeCompiler.new TypeRecord<>(getSpecificFields().mapSecond(
				o -> typeCompiler.new TypeOption<>(o.getSecondOperand().getType()))));
		}
		public <A extends AccessibleVertexTree<?>, E extends Exception> VertexRecordUnion(
				Generator_<Pair<String, A>,E> shared,
				Generator_<Pair<String, VertexOption<AccessibleVertexTree<?>>>,E> specific) throws E {
			super(new VertexSimpleRecord(shared), new VertexRecord<>(specific));
			type = typeCompiler.new TypeRecordUnion(getFirstOperand().getType(), typeCompiler.new TypeRecord<>(getSpecificFields().mapSecond(
				o -> typeCompiler.new TypeOption<>(o.getSecondOperand().getType()))));
		}


		@Override
		public String getFirstLabel() {
			return "sha";
		}
		@Override
		public String getSecondLabel() {
			return "spe";
		}


		@Override
		public TypeRecordUnion getType() {
			return type;
		}


		public PairGenerator<String,AccessibleVertexTree<?>> getSharedFields() {
			return getFirstOperand().getFields();
		}
		public PairGenerator<String,VertexOption<AccessibleVertexTree<?>>> getSpecificFields() {
			return getSecondOperand().getFields();
		}

		public boolean hasSharedField(String field) {
			return getFirstOperand().hasField(field);
		}
		public boolean hasSpecificField(String field) {
			return getSecondOperand().hasField(field);
		}

		public AccessibleVertexTree<?> getSharedField(String field) throws CompilerException {
			return getFirstOperand().getField(field);
		}
		public VertexOption<AccessibleVertexTree<?>> getSpecificField(String field) throws CompilerException {
			return getSecondOperand().getField(field);
		}
	}


	private class VertexUnion
	extends BinaryNode<VertexTree, VertexSimpleUnion, VertexOption<VertexRecordUnion>, HalfArrow<?>>
	implements VertexNode<VertexTree>, AccessibleVertexTree<TypeUnion> {
		final TypeUnion type;

		public VertexUnion(VertexSimpleUnion simpleOptions, VertexOption<VertexRecordUnion> recordOptions) {
			super(simpleOptions, recordOptions);
			type = typeCompiler.new TypeUnion(getFirstOperand().getType(), getRecordOptions().getType());
		}

		@Override
		public String getFirstLabel() {
			return "pri";
		}
		@Override
		public String getSecondLabel() {
			return "rec";
		}

		@Override
		public TypeUnion getType() {
			return type;
		}


		public final BooleanVertexLeaf getHasRecords() {
			return getSecondOperand().getFirstOperand();
		}

		public final VertexRecordUnion getRecordOptions() {
			return getSecondOperand().getSecondOperand();
		}

		public final String hasRecordLabel() {
			return getSecondLabel() + "_" + getSecondOperand().getFirstLabel();
		}

		public final String recordOptionsLabel() {
			return getSecondLabel() + "_" + getSecondOperand().getSecondLabel();
		}

	}



	private final class Builder extends LoggedBuilder {

		/*------- Classes/Interfaces -------*/

		private class PartialReturn {
			private final HalfArrow<?> cond;
			private final Location<Bytecode.If> ifs;
			private PartialReturn tPart;
			private PartialReturn fPart;
			private final List<AccessibleVertexTree<?>> ret;


			public PartialReturn(Location<Bytecode.If> ifs, HalfArrow<?> cond, PartialReturn tPart, PartialReturn fPart) {
				this.cond = cond;
				this.ifs = ifs;
				this.tPart = tPart;
				this.fPart = fPart;
				this.ret = null;
			}
			public PartialReturn(List<AccessibleVertexTree<?>> ret) {
				assert ret != null;
				this.ifs = null;
				this.cond = null;
				this.tPart = null;
				this.fPart = null;
				this.ret = ret;
			}

// TODO
			public Generator<AccessibleVertexTree<?>> getReturn() throws CompilerException {
				assert !isPartial();
//				logger.debug("Return ? "+this+" "+ret+" "+tPart+" "+fPart);
				return ret == null ? tPart.getReturn().gather(fPart.getReturn()).<AccessibleVertexTree<?>, CompilerException>map_(
					(t, f) -> buildEndIf(ifs, cond, t, f)).check() : Generators.fromCollection(ret);
			}

			public void completeReturn(List<AccessibleVertexTree<?>> rem) {
				completeReturn(new PartialReturn(rem));
			}

			public PartialReturn completeReturn(PartialReturn rem) {
				if (ret != null) return this;
				if (tPart == null) tPart = rem;
				else tPart.completeReturn(rem);
				if (fPart == null) fPart = rem;
				else fPart.completeReturn(rem);
				return this;
			}

			public boolean isPartial() {
				return ret == null && ((fPart == null || fPart.isPartial()) || (tPart == null || tPart.isPartial()));
			}

			public void print(String pref) {
				debug(pref+(ret != null ? "Val "+ret : "Mux "+ifs));
				if (ret==null) {
					debug(pref+" t─ "+tPart);
					if (tPart != null)
						tPart.print(pref+" │  ");
					debug(pref+" f─ "+fPart);
					if (fPart != null)
						fPart.print(pref+" │  ");
				}
			}
		}


		private Map<Integer, AccessibleVertexTree<?>> vars = new HashMap<>();
		private Map<Integer, String> identifiers = new HashMap<>();
		private PartialReturn partialReturn = null;
		private final List<AccessibleTypeTree> returnTypes;
		private final DataFlowGraph graph;
//		private Map<wyil.lang.Type, wyvc.lang.Type> compiledTypes = new HashMap<>();

		/*------- Constructor -------*/

		@SuppressWarnings("unchecked")
		public Builder(WyilFile.FunctionOrMethod func) throws CompilerException {
			super(DataFlowGraphBuilder.this.logger);
			graph = new DataFlowGraph();
			Generators.fromCollection(func.type().params()).enumerate().forEach_((Integer k, wyil.lang.Type t) -> {
				identifiers.put(k, ((Location<Bytecode.VariableDeclaration>)func.getTree().getLocation(k)).getBytecode().getName());
				vars.put(k, buildParameter(
					identifiers.get(k),
					buildType(t)));});
			returnTypes = Generators.fromCollection(func.type().returns()).map_(this::buildType).toList();
			build(func.getBody());
/**/			partialReturn.print("RET ");
			partialReturn.getReturn().enumerate().mapFirst(Object::toString).mapFirst("ret_"::concat).forEach_(this::buildReturnValue);
		}


		private void buildReturnValue(String ident, VertexTree ret) {
			if (ret instanceof VertexLeaf)
				graph.new OutputNode(ident, ((VertexLeaf<?>)ret).getValue());
			else if (ret instanceof VertexNode)
				((VertexNode<?>)ret).getComponents().forEach((s, t) -> buildReturnValue(ident+"_"+s, t));
		}


		/*------- Classe content -------*/

//		private Type isNodeType(TypeTree type) throws CompilerException {
//			if (type instanceof CompoundType)
//				CompoundTypeCompilerError.exception(type);
//			return type.getValue();
//		}
//
//		private Type getNodeType(wyil.lang.Type type) throws CompilerException {
//			return Utils.checkedAddIfAbsent(compiledTypes, type, () -> isNodeType(buildType(type)));
//
//		}


		public DataFlowGraph getGraph() {
			return graph;
		}


		public AccessibleTypeTree buildType(wyil.lang.Type type) throws CompilerException {
			return typeCompiler.compileType(type);
		}





		private GeneralVertexLeaf buildUndefinedValue(TypeLeaf type) {
			return new GeneralVertexLeaf(graph.new UndefConstNode(type.getValue()));
		}
		private BooleanVertexLeaf buildUndefinedValue(BooleanTypeLeaf type) {
			return new BooleanVertexLeaf(graph.new UndefConstNode(type.getValue()));
		}
		private VertexSimpleRecord buildUndefinedValue(TypeSimpleRecord type) throws CompilerException {
			return new VertexSimpleRecord(type.getFields().mapSecond_(this::buildUndefinedValue));
		}
		private VertexSimpleUnion buildUndefinedValue(TypeSimpleUnion type) throws CompilerException {
			return new VertexSimpleUnion(type.getOptions().map_(t -> new VertexOption<>(
					buildBoolean(false),
					buildUndefinedValue(t.getSecondOperand()))));
		}
		private VertexRecordUnion buildUndefinedValue(TypeRecordUnion type) throws CompilerException {
			return new VertexRecordUnion(
				type.getSharedFields().mapSecond_(this::buildUndefinedValue),
				type.getSpecificFields().mapSecond_(t -> new VertexOption<AccessibleVertexTree<?>>(
						buildBoolean(false),
						buildUndefinedValue(t.getSecondOperand()))));
		}
		private VertexUnion buildUndefinedValue(TypeUnion type) throws CompilerException {
			return new VertexUnion(
				buildUndefinedValue(type.getFirstOperand()),
				new VertexOption<>(
						buildBoolean(false),
						buildUndefinedValue(type.getRecordOptions())));
		}
		private AccessibleVertexTree<?> buildUndefinedValue(AccessibleTypeTree type) throws CompilerException {
			if (type instanceof BooleanTypeLeaf)
				return buildUndefinedValue((BooleanTypeLeaf)type);
			if (type instanceof TypeLeaf)
				return buildUndefinedValue((TypeLeaf)type);
			if (type instanceof TypeSimpleRecord)
				return buildUndefinedValue((TypeSimpleRecord)type);
			if (type instanceof TypeUnion)
				return buildUndefinedValue((TypeUnion)type);
			if (type instanceof TypeRecordUnion)
				return buildUndefinedValue((TypeRecordUnion)type);
			if (type instanceof TypeSimpleUnion)
				return buildUndefinedValue((TypeSimpleUnion)type);
			throw UnsupportedTreeNodeCompilerError.exception(type);
		}


		private BooleanVertexLeaf buildBoolean(Boolean value) {
			return new BooleanVertexLeaf(graph.new ExternConstNode(Type.Boolean, value.toString()));
		}







		/*------ buildTypedValue ------*/



		private VertexUnion buildTypedValue(AccessibleVertexTree<?> node, TypeUnion type) throws CompilerException {
			if (node instanceof VertexUnion)
				return new VertexUnion(
					buildTypedValue(((VertexUnion) node).getFirstOperand(), type.getFirstOperand()),
					new VertexOption<>(
							((VertexUnion) node).getHasRecords(),
							buildTypedValue(
								((VertexUnion) node).getRecordOptions(),
								type.getRecordOptions())));
			if (node instanceof VertexLeaf || node instanceof VertexSimpleUnion)
				return new VertexUnion(
					buildTypedValue(node, type.getFirstOperand()),
					new VertexOption<>(
							buildBoolean(false),
							buildUndefinedValue(type.getRecordOptions())));
			if (node instanceof VertexSimpleRecord || node instanceof VertexRecordUnion)
				return new VertexUnion(
					buildUndefinedValue(type.getFirstOperand()),
					new VertexOption<>(
							buildBoolean(false),
							buildTypedValue(node, type.getRecordOptions())));
			throw UnrelatedTypeCompilerError.exception(type, node);
		}
		private VertexRecordUnion buildTypedValue(AccessibleVertexTree<?> node, TypeRecordUnion type) throws CompilerException {
			Map<String, AccessibleVertexTree<?>> shared = new HashMap<>();
			Map<String, VertexOption<AccessibleVertexTree<?>>> specific = new HashMap<>();
			if (node instanceof VertexRecordUnion) {
				((VertexRecordUnion) node).getSharedFields().forEach(shared::put);
				((VertexRecordUnion) node).getSpecificFields().forEach(specific::put);
			}
			else if (node instanceof VertexSimpleRecord)
				((VertexSimpleRecord) node).getFields().forEach(shared::put);
			else throw UnrelatedTypeCompilerError.exception(type, node);
			shared.forEach((f, v) -> debug(v.toString(" -- "+f+" ")));
			VertexRecordUnion z =  new VertexRecordUnion(
				type.getSharedFields().map_((s, t) -> new Pair<>(s,buildTypedValue(shared.get(s), t))),
				type.getSpecificFields().map_((s, o) -> new Pair<>(s, shared.containsKey(s)
						? new VertexOption<>(buildBoolean(true), buildTypedValue(shared.get(s), o.getSecondOperand()))
						: specific.containsKey(s) ? new VertexOption<>(specific.get(s).getFirstOperand(), buildTypedValue(specific.get(s).getSecondOperand(), o.getSecondOperand()))
						                          : new VertexOption<>(buildBoolean(false), buildUndefinedValue(o.getSecondOperand())))));
			debug(z.toString("Result "));
			return z; // TODO debug
		}
		private VertexSimpleUnion buildTypedValue(AccessibleVertexTree<?> node, TypeSimpleUnion type) throws CompilerException {
			if (!(node instanceof VertexSimpleUnion) && !(node instanceof VertexLeaf))
				throw UnrelatedTypeCompilerError.exception(type, node);

			List<VertexOption<VertexLeaf<?>>> cpn = new ArrayList<>();
			if (node instanceof VertexSimpleUnion)
				((VertexSimpleUnion)node).getOptions().forEach(cpn::add);
			else if (node instanceof VertexLeaf)
				cpn.add(new VertexOption<>(buildBoolean(true), (VertexLeaf<?>) node));
			else throw UnrelatedTypeCompilerError.exception(type, node);
			return new VertexSimpleUnion(type.getOptions().<VertexOption<VertexLeaf<?>>>map(o -> {
				for (VertexOption<VertexLeaf<?>> c : cpn) {
					if (o.getSecondOperand().getValue().equals(c.getSecondOperand().getType()))
						return c;
				}
				return new VertexOption<VertexLeaf<?>>(buildBoolean(false), buildUndefinedValue(o.getSecondOperand()));
			}));
		}
		private VertexSimpleRecord buildTypedValue(AccessibleVertexTree<?> node, TypeSimpleRecord type) throws CompilerException {
			if (node instanceof VertexSimpleRecord && ((VertexSimpleRecord) node).hasSameFields(type))
				return new VertexSimpleRecord(type.getFields().duplicateFirst().mapSecond_(((VertexSimpleRecord) node)::getField).map23(this::buildTypedValue));
			throw UnrelatedTypeCompilerError.exception(type, node);
		}
		private AccessibleVertexTree<?> buildTypedValue(AccessibleVertexTree<?> node, AccessibleTypeTree type) throws CompilerException {
//			debug("Val "+(node == null ? "NULL" : node.getType())+" "+type);
//			openLevel("Build Typed Value");
//			debugLevel(node.toString("Node "));
//			debugLevel(type.toString("Type "));
			if (node.isStructuredAs(type)){
				if (node.getValues().gather(type.getValues()).mapFirst(h -> h.node.type).forAll(Type::equals))
					return node;
				throw UnrelatedTypeCompilerError.exception(type, node);
			}
			if (type instanceof TypeSimpleRecord)
				return buildTypedValue(node, (TypeSimpleRecord)type);
			if (type instanceof TypeUnion)
				return buildTypedValue(node, (TypeUnion)type);
			if (type instanceof TypeSimpleUnion)
				return buildTypedValue(node, (TypeSimpleUnion)type);
			if (type instanceof TypeRecordUnion)
				return buildTypedValue(node, (TypeRecordUnion)type);
			throw UnrelatedTypeCompilerError.exception(type, node);
		}



		/*------ buildParameter ------*/

		private GeneralVertexLeaf buildParameter(String ident, TypeLeaf type) {
			return new GeneralVertexLeaf(graph.new InputNode(ident, type.getValue()));
		}
		private BooleanVertexLeaf buildParameter(String ident, BooleanTypeLeaf type) {
			return new BooleanVertexLeaf(graph.new InputNode(ident, type.getValue()));
		}
		private VertexSimpleRecord buildParameter(String ident, TypeSimpleRecord type) throws CompilerException {
			return new VertexSimpleRecord(type.getFields().duplicateFirst().mapSecond((ident+"_")::concat).map23_(this::buildParameter));
		}
		private VertexSimpleUnion buildParameter(String ident, TypeSimpleUnion type) throws CompilerException {
			return new VertexSimpleUnion(type.getOptions().enumerate().mapFirst(type::getLabel).mapFirst((ident+"_")::concat).map(
				(s, t) -> new VertexOption<>(
					buildParameter(s+"_"+t.getFirstLabel(), t.getFirstOperand()),
					buildParameter(s+"_"+t.getSecondLabel(), t.getSecondOperand()))));
		}
		private VertexRecordUnion buildParameter(String ident, TypeRecordUnion type) throws CompilerException {
			return new VertexRecordUnion(
				type.getSharedFields().computeSecond_((f, t) -> buildParameter(ident+"_"+f, t)),
				type.getSpecificFields().computeSecond_((f, t) -> new VertexOption<>(
						buildParameter(ident+"_"+f+"_"+t.getFirstLabel(), t.getFirstOperand()),
						buildParameter(ident+"_"+f+"_"+t.getSecondLabel(), t.getSecondOperand()))));
		}
		private VertexUnion buildParameter(String ident, TypeUnion type) throws CompilerException {
			return new VertexUnion(
				buildParameter(ident+"_"+type.getFirstLabel(), type.getFirstOperand()),
				new VertexOption<>(
						buildParameter(ident+"_"+type.hasRecordLabel(), type.getHasRecords()),
						buildParameter(ident+"_"+type.recordOptionsLabel(), type.getRecordOptions())));
		}
		private AccessibleVertexTree<?> buildParameter(String ident, AccessibleTypeTree type) throws CompilerException {
			if (type instanceof BooleanTypeLeaf)
				return buildParameter(ident, (BooleanTypeLeaf)type);
			if (type instanceof TypeLeaf)
				return buildParameter(ident, (TypeLeaf)type);
			if (type instanceof TypeSimpleRecord)
				return buildParameter(ident, (TypeSimpleRecord)type);
			if (type instanceof TypeUnion)
				return buildParameter(ident, (TypeUnion)type);
			if (type instanceof TypeRecordUnion)
				return buildParameter(ident, (TypeRecordUnion)type);
			if (type instanceof TypeSimpleUnion)
				return buildParameter(ident, (TypeSimpleUnion)type);
			throw UnsupportedTreeNodeCompilerError.exception(type);
		}



		@SuppressWarnings("unchecked")
		public void build(Location<?> location) throws CompilerException {
/**/			openLevel("Build");
/**/			Utils.printLocation(logger, location, level);
			Bytecode bytecode = location.getBytecode();
			if (bytecode instanceof Bytecode.Block)
				buildBlock((Location<Bytecode.Block>) location);
			else if (bytecode instanceof Bytecode.VariableDeclaration)
				buildDecl((Location<Bytecode.VariableDeclaration>) location);
			else if (bytecode instanceof Bytecode.Assign)
				buildAssign((Location<Bytecode.Assign>) location);
			else if (bytecode instanceof Bytecode.Return)
				buildReturn((Location<Bytecode.Return>) location);
			else if (bytecode instanceof Bytecode.If)
				buildIf((Location<Bytecode.If>) location);
			else if (bytecode instanceof Bytecode.While)
				buildWhile((Location<Bytecode.While>) location);
			else if (bytecode instanceof Bytecode.Skip)
				graph.addSeparation();
			else
				throw WyilUnsupportedCompilerError.exception(location);
/**/			closeLevel();
		}

		private void buildBlock(Location<Bytecode.Block> block) throws CompilerException {
			for (Location<?> l : block.getOperands())
				build(l);
		}




		@SuppressWarnings("unchecked")
		private List<AccessibleVertexTree<?>> buildTuple(Location<?>[] elem) throws CompilerException {
			ArrayList<AccessibleVertexTree<?>> exprs = new ArrayList<>();
			for (Location<?> e : elem){
				if (e.getBytecode() instanceof Bytecode.Invoke)
					exprs.addAll(buildInvoke((Location<Bytecode.Invoke>) e));
				else
					exprs.add(buildExpression(e));
			}
			return exprs;
		}




		/*------ buildNamedHalfArrow ------*/

		private GeneralVertexLeaf buildNamedHalfArrow(String ident, VertexLeaf<?> val) {
			return new GeneralVertexLeaf(val.getValue().node, ident);
		}
		private BooleanVertexLeaf buildNamedHalfArrow(String ident, BooleanVertexLeaf val) {
			return new BooleanVertexLeaf(val.getValue().node, ident);
		}
		private VertexSimpleRecord buildNamedHalfArrow(String ident, VertexSimpleRecord val) throws CompilerException {
			return new VertexSimpleRecord(val.getFields().duplicateFirst().mapSecond((ident+"_")::concat).map23_(this::buildNamedHalfArrow));
		}
		private VertexSimpleUnion buildNamedHalfArrow(String ident, VertexSimpleUnion val) throws CompilerException {
			return new VertexSimpleUnion(val.getOptions().enumerate().mapFirst(val::getLabel).mapFirst((ident+"_")::concat).map(
				(s, t) -> new VertexOption<>(
					buildNamedHalfArrow(s+"_"+t.getFirstLabel(), t.getFirstOperand()),
					buildNamedHalfArrow(s+"_"+t.getSecondLabel(), t.getSecondOperand()))));
		}
		private VertexRecordUnion buildNamedHalfArrow(String ident, VertexRecordUnion val) throws CompilerException {
			return new VertexRecordUnion(
				val.getSharedFields().biMap_(p -> p.first, p -> buildNamedHalfArrow(ident+"_"+p.first, p.second)),
				val.getSpecificFields().computeSecond_((f, t) -> new VertexOption<>(
						buildNamedHalfArrow(ident+"_"+f+"_"+t.getFirstLabel(), t.getFirstOperand()),
						buildNamedHalfArrow(ident+"_"+f+"_"+t.getSecondLabel(), t.getSecondOperand()))));
		}
		private VertexUnion buildNamedHalfArrow(String ident, VertexUnion val) throws CompilerException {
			return new VertexUnion(
				buildNamedHalfArrow(ident+"_"+val.getFirstLabel(), val.getFirstOperand()),
				new VertexOption<>(
						buildNamedHalfArrow(ident+"_"+val.hasRecordLabel(), val.getHasRecords()),
						buildNamedHalfArrow(ident+"_"+val.recordOptionsLabel(), val.getRecordOptions())));
		}
		private AccessibleVertexTree<?> buildNamedHalfArrow(String ident, AccessibleVertexTree<?> val) throws CompilerException {
//			debug(val.toString(ident+" > "));
			if (val instanceof BooleanVertexLeaf)
				return buildNamedHalfArrow(ident, (BooleanVertexLeaf)val);
			if (val instanceof VertexLeaf)
				return buildNamedHalfArrow(ident, (VertexLeaf<?>)val);
			if (val instanceof VertexSimpleRecord)
				return buildNamedHalfArrow(ident, (VertexSimpleRecord)val);
			if (val instanceof VertexSimpleUnion)
				return buildNamedHalfArrow(ident, (VertexSimpleUnion)val);
			if (val instanceof VertexRecordUnion)
				return buildNamedHalfArrow(ident, (VertexRecordUnion)val);
			if (val instanceof VertexUnion)
				return buildNamedHalfArrow(ident, (VertexUnion)val);
			throw UnsupportedTreeNodeCompilerError.exception(val);
		}




		@SuppressWarnings("unchecked")
		public AssignDeque buildDeque(Location<?> location) throws CompilerException {
			Bytecode bytecode = location.getBytecode();
			if (bytecode instanceof Bytecode.VariableDeclaration)
				return new DeclarationDeque((Location<Bytecode.VariableDeclaration>) location);
			AssignDeque previous = buildDeque(location.getOperand(0));
			if (bytecode instanceof Bytecode.FieldLoad)
				return new FieldLoadDeque( (Location<Bytecode.FieldLoad>) location);
			if (bytecode instanceof Bytecode.VariableAccess)
				return previous;
			if (bytecode instanceof Bytecode.AliasDeclaration)
				return previous;
			throw WyilUnsupportedCompilerError.exception(location);
		}

		private abstract class AssignDeque {
			public abstract void buildAssign(AccessibleVertexTree<?> assign) throws CompilerException;
			protected abstract AccessibleVertexTree<?> getValue();

		};

		private class DeclarationDeque extends AssignDeque {
			private final int index;
			private final String ident;
			private final AccessibleTypeTree type;
			private final AccessibleVertexTree<?> value;

			public DeclarationDeque(Location<Bytecode.VariableDeclaration> declaration) throws CompilerException {
				index = declaration.getIndex();
				ident = declaration.getBytecode().getName();
				type = buildType(declaration.getType());
				value = vars.get(declaration.getIndex());
			}

			@Override
			public void buildAssign(AccessibleVertexTree<?> assign) throws CompilerException {
				AccessibleVertexTree<?> r = buildTypedValue(assign, type);
				vars.put(index, buildNamedHalfArrow(ident, r));
//				debugLevel("Assign de " + ident);
//				debugLevel(assign.toString("  "));
//				debugLevel(r.toString("  "));

			}

			@Override
			protected AccessibleVertexTree<?> getValue() {
				return value;
			}
		}
		private class FieldLoadDeque extends AssignDeque {
			private final AssignDeque previous;
			private final String fieldname;
			private final AccessibleVertexTree<?> value;
			private final AccessibleVertexTree<?> fieldValue;

			public FieldLoadDeque(Location<Bytecode.FieldLoad> field) throws CompilerException {
				previous = buildDeque(field.getOperand(0));
				fieldname = field.getBytecode().fieldName();
				value = previous.getValue();
				if (value instanceof VertexSimpleRecord)
					fieldValue = ((VertexSimpleRecord)value).getField(fieldname);
				else if (value instanceof VertexRecordUnion)
					fieldValue = ((VertexRecordUnion)value).getSharedField(fieldname);
				else if (value instanceof VertexUnion)
					fieldValue = ((VertexUnion)value).getRecordOptions().getSharedField(fieldname);
				else
					throw UnsupportedAssignmentCompilerError.exception(field, value);
			}

			@Override
			public void buildAssign(AccessibleVertexTree<?> assign) throws CompilerException {
				if (value instanceof VertexSimpleRecord)
					previous.buildAssign(new VertexSimpleRecord(((VertexSimpleRecord)value).getFields().computeSecond_((String f, AccessibleVertexTree<?> v)
						-> f.equals(fieldname) ? buildTypedValue(assign, v.getType()) : v)));
				else if (value instanceof VertexRecordUnion)
					previous.buildAssign(new VertexRecordUnion(
						((VertexRecordUnion)value).getSharedFields().computeSecond_((String f, AccessibleVertexTree<?> v)
							-> f.equals(fieldname) ? buildTypedValue(assign, v.getType()) : v),
						((VertexRecordUnion)value).getSpecificFields().toChecked()));
				else if (value instanceof VertexUnion)
					previous.buildAssign(new VertexUnion(
						((VertexUnion)value).getFirstOperand(),
						new VertexOption<>(
								((VertexUnion)value).getHasRecords(),
							new VertexRecordUnion(
								((VertexUnion)value).getRecordOptions().getSharedFields().computeSecond_((String f, AccessibleVertexTree<?> v)
									-> f.equals(fieldname) ? buildTypedValue(assign, v.getType()) : v),
								((VertexUnion)value).getRecordOptions().getSpecificFields().toChecked()))));
			}

			@Override
			protected AccessibleVertexTree<?> getValue() {
				return fieldValue;
			}
		}/*// TODO ?
		private class AliasDeque extends AssignDeque {
			private final AssignDeque previous;
			private final AccessibleVertexTree value;
			private final AccessibleVertexTree aliasValue;

			public AliasDeque(Location<Bytecode.AliasDeclaration> alias) throws CompilerException {
				previous = buildDeque(alias.getOperand(0));
				value = previous.getValue();
				aliasValue = buildAlias(buildType(alias.getType()), value);

			}

			@Override
			protected AccessibleVertexTree getValue() {
				return aliasValue;
			}
		}*/


		private void buildAssignValue(Location<?> acc, AccessibleVertexTree<?> val) throws CompilerException {
//			openLevel("Assign");
//			Utils.printLocation(logger, acc, level);
			buildDeque(acc).buildAssign(val);
//			closeLevel();
		}

		private void buildAssign(Location<Bytecode.Assign> assign) throws CompilerException {
			Location<?>[] lhs = assign.getOperandGroup(SyntaxTree.LEFTHANDSIDE);
			Location<?>[] rhs = assign.getOperandGroup(SyntaxTree.RIGHTHANDSIDE);
			List<AccessibleVertexTree<?>> brhs = buildTuple(rhs);
			for(int k = 0; k < lhs.length; ++k)
				buildAssignValue(lhs[k], brhs.get(k));
		}








		private void buildReturn(Location<Bytecode.Return> ret) throws CompilerException {
//			debug("Return.....");
			Generator_<AccessibleVertexTree<?>,CompilerException> retv = Generators.fromCollection(
				buildTuple(ret.getOperands())).enumerate().mapFirst(returnTypes::get).swap().map_(this::buildTypedValue);
//			debug("Return ?.....");
			if (partialReturn != null)
				partialReturn.completeReturn(retv.toList());
			else
				partialReturn = new PartialReturn(retv.toList());
		}

		private void buildDecl(Location<Bytecode.VariableDeclaration> decl) throws CompilerException {
//			debug("Declaration de "+decl.getBytecode().getName()+" en "+decl.getIndex());
			identifiers.put(decl.getIndex(), decl.getBytecode().getName());
			vars.put(decl.getIndex(), decl.numberOfOperands() == 1
				? buildNamedHalfArrow(decl.getBytecode().getName(),
					buildTypedValue(buildExpression(decl.getOperand(0)), buildType(decl.getType())))
				: null);
//			debug("Ajouté !");
		}




		private AccessibleVertexTree<?> buildRecordConstruction(Location<Bytecode.Operator> op) throws CompilerException {
			AccessibleTypeTree type = buildType(op.getType());
			if (type instanceof TypeSimpleRecord)
				return new VertexSimpleRecord(
						Generators.fromCollection(((wyil.lang.Type.EffectiveRecord)op.getType()).getFieldNames()).gather(
							Generators.fromCollection(op.getOperands())).mapSecond_(l-> buildExpression(l)));
			throw WyilUnsupportedCompilerError.exception(op); // TODO Record Union ? + Type verification
		}


		private BiFunction_<BooleanVertexLeaf,BooleanVertexLeaf,BooleanVertexLeaf,CompilerException> buildBooleanFolding(Location<Bytecode.Operator> is, BinaryOperation op) {
			return (b, c) -> new BooleanVertexLeaf(graph.new BinOpNode(is, op, Type.Boolean, b.getValue(), c.getValue()));
		}

		private BooleanVertexLeaf buildFlowTyping(Location<Bytecode.Operator> is, TypeSimpleRecord type, VertexSimpleRecord value) throws CompilerException {
			if (!type.hasSameFields(value)) {
				logger.addMessage(new IncompatibleTypeCompilerNotice(value, type));
				return buildBoolean(false);
			}
			return Generators.constant(is).gatherPair(type.getFields().takeSecond().gather(value.getFields().takeSecond())).
					map_(this::buildFlowTyping).fold(buildBooleanFolding(is, BinaryOperation.And), buildBoolean(true));
		}
		private BooleanVertexLeaf buildFlowTyping(Location<Bytecode.Operator> is, TypeSimpleRecord type, VertexRecordUnion value) throws CompilerException {
			return type.getFields().map_((f,t) -> {
				if (value.hasSharedField(f))
					return buildFlowTyping(is, t, value.getSharedField(f));
				if (value.hasSpecificField(f))
					return buildBooleanFolding(is, BinaryOperation.And).apply(
						value.getSpecificField(f).getFirstOperand(),
						buildFlowTyping(is, t, value.getSpecificField(f).getSecondOperand()));
//				debug("Attention à "+f+" dans "+value.getSharedFields().takeFirst().toList());
				throw UnsupportedFlowTypingCompilerError.exception(type, value);
			}).fold(buildBooleanFolding(is, BinaryOperation.And), buildBoolean(true));
//			return Generators.constant(is).gatherPair(type.getFields().takeSecond().gather(value.getFields().takeSecond())).
//					map_(this::buildFlowTyping)
		}
		private BooleanVertexLeaf buildFlowTyping(Location<Bytecode.Operator> is, TypeLeaf type, VertexSimpleUnion value) throws CompilerException {
			for (VertexOption<? extends VertexLeaf<?>> o : value.getOptions().toList())
				if (o.getSecondOperand().getType().equals(type)) // TODO plus compliqué !
					return o.getFirstOperand();
			throw UnsupportedFlowTypingCompilerError.exception(type, value);
		}
		private BooleanVertexLeaf buildFlowTyping(Location<Bytecode.Operator> is, TypeSimpleUnion type, VertexSimpleUnion value) throws CompilerException {
			return Generators.fromPairSingleton(is, value).addComponent(type.getOptions()).swap23().mapSecond(TypeOption::getSecondOperand).
					map_(this::buildFlowTyping).fold(buildBooleanFolding(is, BinaryOperation.Or), buildBoolean(false));
		}
		private BooleanVertexLeaf buildFlowTyping(Location<Bytecode.Operator> is, AccessibleTypeTree type, AccessibleVertexTree<?> value) throws CompilerException {
			if (value.getType().equals(type))
				return buildBoolean(true);
			if (type instanceof TypeSimpleRecord && value instanceof VertexSimpleRecord)
				return buildFlowTyping(is, (TypeSimpleRecord)type, (VertexSimpleRecord)value);
			if (type instanceof TypeLeaf && value instanceof VertexSimpleUnion)
				return buildFlowTyping(is, (TypeLeaf)type, (VertexSimpleUnion)value);
			if (type instanceof TypeLeaf && value instanceof VertexUnion)
				return buildFlowTyping(is, (TypeLeaf)type, ((VertexUnion)value).getFirstOperand());
			if (type instanceof TypeSimpleUnion && value instanceof VertexSimpleUnion)
				return buildFlowTyping(is, (TypeSimpleUnion)type, ((VertexSimpleUnion)value));
			if (type instanceof TypeSimpleUnion && value instanceof VertexUnion)
				return buildFlowTyping(is, (TypeSimpleUnion)type, ((VertexUnion)value).getFirstOperand());
			if (type instanceof TypeSimpleRecord && value instanceof VertexRecordUnion)
				return buildFlowTyping(is, (TypeSimpleRecord)type, ((VertexRecordUnion)value));
			if (type instanceof TypeSimpleRecord && value instanceof VertexUnion)
				return buildFlowTyping(is, (TypeSimpleRecord)type, (VertexUnion)value);
			throw UnsupportedFlowTypingCompilerError.exception(type, value);
		}

		private BooleanVertexLeaf buildFlowTyping(Location<Bytecode.Operator> is, TypeSimpleRecord type, VertexUnion value) throws CompilerException {
			return new BooleanVertexLeaf(graph.new BinOpNode(is, BinaryOperation.And, Type.Boolean,
				value.getHasRecords().getValue(),
				buildFlowTyping(is, type, value.getRecordOptions()).getValue()));
		}


		private BooleanVertexLeaf buildIs(Location<Bytecode.Operator> is) throws CompilerException {
			return buildFlowTyping(is,
				buildType(((Constant.Type)(((Bytecode.Const)(is.getOperand(1).getBytecode())).constant())).value()),
				buildExpression(is.getOperand(0)));
		}

		private UnaryOperation buildUnaryOperationKind(Location<Bytecode.Operator> op) throws CompilerException {
			switch (op.getBytecode().kind()) {
			case NOT: 	return UnaryOperation.Not;
			default:	throw UnsupportedOperatorCompilerError.exception(op);
			}
		}

		private AccessibleVertexTree<?> buildUnaryOperation(Location<Bytecode.Operator> op) throws CompilerException {
			AccessibleTypeTree type = buildType(op.getType());
			AccessibleVertexTree<?> arg = buildTypedValue(buildExpression(op.getOperand(0)), buildType(op.getType()));
			if (type instanceof TypeLeaf && arg instanceof VertexLeaf)
				return new GeneralVertexLeaf(graph.new UnaOpNode(op, buildUnaryOperationKind(op),
					((TypeLeaf) type).getValue(), ((VertexLeaf<?>) arg).getValue()));
			throw WyilUnsupportedCompilerError.exception(op);
		}


		private BinaryOperation buildBinaryOperationKind(Location<Bytecode.Operator> op) throws CompilerException {
			switch (op.getBytecode().kind()) {
			case ADD: 	return BinaryOperation.Add;
			case SUB: 	return BinaryOperation.Sub;
			case MUL: 	return BinaryOperation.Mul;
			case DIV: 	return BinaryOperation.Div;
			case REM: 	return BinaryOperation.Rem;
			case BITWISEOR:
			case OR: 	return BinaryOperation.Or;
			case BITWISEAND:
			case AND: 	return BinaryOperation.And;
			case BITWISEXOR: return BinaryOperation.And;
			case EQ: 	return BinaryOperation.Eq;
			case NEQ: 	return BinaryOperation.Ne;
			case LT: 	return BinaryOperation.Lt;
			case LTEQ: 	return BinaryOperation.Le;
			case GT: 	return BinaryOperation.Gt;
			case GTEQ: 	return BinaryOperation.Ge;
			default:	throw UnsupportedOperatorCompilerError.exception(op);
			}
		}

		private AccessibleVertexTree<?> buildBinaryOperation(Location<Bytecode.Operator> op) throws CompilerException {
			AccessibleTypeTree type = buildType(op.getType());
			AccessibleVertexTree<?> arg1 = buildExpression(op.getOperand(0));
			AccessibleVertexTree<?> arg2 = buildExpression(op.getOperand(1));
			if (type instanceof TypeLeaf && arg1 instanceof VertexLeaf && arg2 instanceof VertexLeaf)
				return new GeneralVertexLeaf(graph.new BinOpNode(op, buildBinaryOperationKind(op),
					((TypeLeaf) type).getValue(), ((VertexLeaf<?>) arg1).getValue(), ((VertexLeaf<?>) arg2).getValue()));
			throw WyilUnsupportedCompilerError.exception(op);
		}
		private AccessibleVertexTree<?> buildOperator(Location<Bytecode.Operator> op) throws CompilerException {
//			debug("Operator Compilation");
//			debug("Operator "+a);
//			a.printStructure(logger, "  ");
			if (op.getBytecode().kind() == OperatorKind.RECORDCONSTRUCTOR)
				return buildRecordConstruction(op);
			if (op.getBytecode().kind() == OperatorKind.IS)
				return buildIs(op);
			if (op.numberOfOperands() == 1)
				return buildUnaryOperation(op);
			if (op.numberOfOperands() == 2)
				return buildBinaryOperation(op);
			throw WyilUnsupportedCompilerError.exception(op);
		}

		private AccessibleVertexTree<?> buildConst(Location<Bytecode.Const> val) throws CompilerException {
			TypeTree t = buildType(val.getType());
			logger.debug(t.toString(""));
			if (t instanceof BooleanTypeLeaf)
				return new BooleanVertexLeaf(graph.new ConstNode(val, ((TypeLeaf) t).getValue()));
			if (t instanceof TypeLeaf)
				return new GeneralVertexLeaf(graph.new ConstNode(val, ((TypeLeaf) t).getValue()));
			throw WyilUnsupportedCompilerError.exception(val);
		}




		private AccessibleVertexTree<?> buildFieldAccess(Location<Bytecode.FieldLoad> field) throws CompilerException {
			AccessibleVertexTree<?> record = buildAccess(field.getOperand(0));
			if (record instanceof VertexSimpleRecord)
				return ((VertexSimpleRecord)record).getField(field.getBytecode().fieldName());
			if (record instanceof VertexRecordUnion)
				return ((VertexRecordUnion)record).getFirstOperand().getField(field.getBytecode().fieldName());
			throw WyilUnsupportedCompilerError.exception(field);
		}



		private VertexLeaf<?> buildAlias(TypeLeaf type, VertexSimpleUnion node) throws CompilerException {
			return node.getOptions().map(VertexOption::getSecondOperand).find(o -> o.getType().equals(type));
		}
		private VertexSimpleRecord buildAlias(TypeSimpleRecord type, VertexSimpleRecord node) throws CompilerException {
			if (! type.hasSameFields(node))
				throw UnsupportedAliasCompilerError.exception(type, node);
			return new VertexSimpleRecord(type.getFields().addComponent(node.getFields().takeSecond()).map23_(this::buildAlias));
		}
		private VertexSimpleRecord buildAlias(TypeSimpleRecord type, VertexRecordUnion node) throws CompilerException {
			Map<String, AccessibleVertexTree<?>> cpn = new HashMap<>();
			node.getSharedFields().forEach(cpn::put);
			node.getSpecificFields().mapSecond(VertexOption::getSecondOperand).forEach(cpn::put);
			if (! type.getFieldNames().forAll(cpn::containsKey))
				throw UnsupportedAliasCompilerError.exception(type, node);
			return new VertexSimpleRecord(type.getFields().duplicateFirst().mapSecond(cpn::get).swap23().map23_(this::buildAlias));
		}
		private VertexSimpleUnion buildAlias(TypeSimpleUnion type, VertexSimpleUnion node) throws CompilerException {
			List<VertexOption<VertexLeaf<?>>> cpn = node.getOptions().toList();
			return new VertexSimpleUnion(type.getOptions().map(TypeOption::getSecondOperand).map(l -> Generators.fromCollection(cpn).find(
				o -> l.equals(o.getSecondOperand().getType()))));
		}
		private VertexRecordUnion buildAlias(TypeRecordUnion type, VertexRecordUnion node) throws CompilerException {
			return new VertexRecordUnion(
				type.getSharedFields().computeSecond_((f, t) -> buildAlias(t, node.hasSharedField(f)
					? node.getSharedField(f)
					: node.getSpecificField(f).getSecondOperand())),
				type.getSpecificFields().computeSecond_(
					(f, t) -> new VertexOption<>(
							node.getSpecificField(f).getFirstOperand(), buildAlias(t.getSecondOperand(), node.getSpecificField(f).getSecondOperand()))));
		}
		private VertexUnion buildAlias(TypeUnion type, VertexUnion node) throws CompilerException {
			return new VertexUnion(
				buildAlias(type.getFirstOperand(), node.getFirstOperand()),
				new VertexOption<>(
						node.getHasRecords(),
						buildAlias(type.getRecordOptions(), node.getRecordOptions())));
		}
		private AccessibleVertexTree<?> buildAlias(AccessibleTypeTree type, AccessibleVertexTree<?> node) throws CompilerException {
			// TODO BooleanLeaf ?
			if (node.getType().equals(type))
				return node;
			if (type instanceof TypeLeaf && node instanceof VertexSimpleUnion)
				return buildAlias((TypeLeaf) type, (VertexSimpleUnion) node);
			if (type instanceof TypeLeaf && node instanceof VertexUnion)
				return buildAlias((TypeLeaf) type, ((VertexUnion) node).getFirstOperand());
			if (type instanceof TypeSimpleRecord && node instanceof VertexSimpleRecord)
				return buildAlias((TypeSimpleRecord) type, (VertexSimpleRecord) node);
			if (type instanceof TypeSimpleRecord && node instanceof VertexRecordUnion)
				return buildAlias((TypeSimpleRecord) type, (VertexRecordUnion) node);
			if (type instanceof TypeSimpleRecord && node instanceof VertexUnion)
				return buildAlias((TypeSimpleRecord) type, ((VertexUnion) node).getRecordOptions());
			if (type instanceof TypeSimpleUnion && node instanceof VertexSimpleUnion)
				return buildAlias((TypeSimpleUnion) type, (VertexSimpleUnion) node);
			if (type instanceof TypeSimpleUnion && node instanceof VertexUnion)
				return buildAlias((TypeSimpleUnion) type, ((VertexUnion) node).getFirstOperand());
			if (type instanceof TypeRecordUnion && node instanceof VertexRecordUnion)
				return buildAlias((TypeRecordUnion) type, (VertexRecordUnion) node);
			if (type instanceof TypeRecordUnion && node instanceof VertexUnion)
				return buildAlias((TypeRecordUnion) type, ((VertexUnion) node).getRecordOptions());
			if (type instanceof TypeUnion && node instanceof VertexUnion)
				return buildAlias((TypeUnion) type, (VertexUnion) node);
			throw UnsupportedAliasCompilerError.exception(type, node);
		}

		private AccessibleVertexTree<?> buildVariableAccess(Location<?> var) throws CompilerException {
			Bytecode bytecode = var.getBytecode();
//			debug("var access"+var+" "+var.getIndex());
			if (bytecode instanceof Bytecode.VariableDeclaration)
				return vars.get(var.getIndex());
			if (bytecode instanceof Bytecode.AliasDeclaration)
				return buildAlias(buildType(var.getType()), buildVariableAccess(var.getOperand(0)));
			throw WyilUnsupportedCompilerError.exception(var);
		}
//

		@SuppressWarnings("unchecked")
		private AccessibleVertexTree<?> buildAccess(Location<?> location) throws CompilerException {
/**/			openLevel("Access");
/**/			Utils.printLocation(logger, location, level);
			Bytecode bytecode = location.getBytecode();
			if (bytecode instanceof Bytecode.FieldLoad)
				return end(buildFieldAccess((Location<Bytecode.FieldLoad>) location));
			if (bytecode instanceof Bytecode.VariableAccess)
				return end(buildVariableAccess(location.getOperand(0)));
			throw new CompilerException(new WyilUnsupportedCompilerError(location));
		}



		private GeneralVertexLeaf buildCallReturn(String ident, TypeLeaf type, FuncCallNode func) {
			return new GeneralVertexLeaf(graph.new FunctionReturnNode(ident, type.getValue(),func));
		}
		private BooleanVertexLeaf buildCallReturn(String ident, BooleanTypeLeaf type, FuncCallNode func) {
			return new BooleanVertexLeaf(graph.new FunctionReturnNode(ident, type.getValue(),func));
		}
		private VertexSimpleRecord buildCallReturn(String ident, TypeSimpleRecord type, FuncCallNode func) throws CompilerException {
			return new VertexSimpleRecord(type.getFields().duplicateFirst().mapSecond((ident+"_")::concat).map23_(
				(i, t) -> buildCallReturn(i,t,func)));
		}
		private VertexSimpleUnion buildCallReturn(String ident, TypeSimpleUnion type, FuncCallNode func) throws CompilerException {
			return new VertexSimpleUnion(type.getOptions().enumerate().mapFirst(type::getLabel).mapFirst((ident+"_")::concat).map(
				(s, t) -> new VertexOption<>(
					buildCallReturn(s+"_"+t.getFirstLabel(), t.getFirstOperand(), func),
					buildCallReturn(s+"_"+t.getSecondLabel(), t.getSecondOperand(), func))));
		}
		private VertexRecordUnion buildCallReturn(String ident, TypeRecordUnion type, FuncCallNode func) throws CompilerException {
			return new VertexRecordUnion(
				type.getSharedFields().computeSecond_((f, t) -> buildCallReturn(ident+"_"+f, t, func)),
				type.getSpecificFields().computeSecond_((f, t) -> new VertexOption<>(
						buildCallReturn(ident+"_"+t+"_"+t.getFirstLabel(), t.getFirstOperand(), func),
						buildCallReturn(ident+"_"+t+"_"+t.getSecondLabel(), t.getSecondOperand(), func))));
		}
		private VertexUnion buildCallReturn(String ident, TypeUnion type, FuncCallNode func) throws CompilerException {
			return new VertexUnion(
				buildCallReturn(ident+"_"+type.getFirstLabel(), type.getFirstOperand(), func),
				new VertexOption<>(
						buildCallReturn(ident+"_"+type.hasRecordLabel(), type.getHasRecords(), func),
						buildCallReturn(ident+"_"+type.recordOptionsLabel(), type.getRecordOptions(), func)));
		}

		private AccessibleVertexTree<?> buildCallReturn(String ident, AccessibleTypeTree type, FuncCallNode func) throws CompilerException {
			if (type instanceof BooleanTypeLeaf)
				return buildCallReturn(ident, (BooleanTypeLeaf)type, func);
			if (type instanceof TypeLeaf)
				return buildCallReturn(ident, (TypeLeaf)type, func);
			if (type instanceof TypeSimpleRecord)
				return buildCallReturn(ident, (TypeSimpleRecord)type, func);
			if (type instanceof TypeUnion)
				return buildCallReturn(ident, (TypeUnion)type, func);
			if (type instanceof TypeRecordUnion)
				return buildCallReturn(ident, (TypeRecordUnion)type, func);
			if (type instanceof TypeSimpleUnion)
				return buildCallReturn(ident, (TypeSimpleUnion)type, func);
			throw UnsupportedTreeNodeCompilerError.exception(type);
		}

		private List<AccessibleVertexTree<?>> buildInvoke(Location<Bytecode.Invoke> call) throws CompilerException {
			openLevel("INVOKE");
			debugLevel("Param "+call.getBytecode().type().params().length + " Op "+call.numberOfOperands());
			FuncCallNode c = graph.new FuncCallNode(call,
				Generators.concat(Generators.fromCollection(call.getBytecode().type().params()).enumerate().duplicateFirst().
					mapFirst(Object::toString).mapSecond(call::getOperand).map_("arg"::concat, this::buildExpression, this::buildType).
					map23(this::buildTypedValue).map(this::buildNamedHalfArrow).map(VertexTree::getValues)).toList());
//			FuncCallNode c = graph.new FuncCallNode(call,
//				Generators.concat(Generators.fromCollection(call.getOperands()).enumerate().map_(
//				    (Integer t, Location<?> l) -> buildNamedHalfArrow("arg_"+t, buildExpression(l))).map(VertexTree::getValues)).toList());
			return Generators.fromCollection(call.getBytecode().type().returns()).enumerate().mapFirst(Object::toString).mapFirst("ret_"::concat).
					addComponent(Generators.constant(c)).mapSecond_(this::buildType).<AccessibleVertexTree<?>>map(this::buildCallReturn).toList();
		}


		@SuppressWarnings("unchecked")
		private AccessibleVertexTree<?> buildExpression(Location<?> location) throws CompilerException {
			openLevel("Expression");
			Utils.printLocation(logger, location, level);
			Bytecode bytecode = location.getBytecode();
			if (bytecode instanceof Bytecode.Operator)
				return end(buildOperator((Location<Bytecode.Operator>) location));
			if (bytecode instanceof Bytecode.Const)
				return end(buildConst((Location<Bytecode.Const>) location));
			if (bytecode instanceof Bytecode.VariableAccess || bytecode instanceof Bytecode.FieldLoad)
				return end(buildAccess(location));
			if (bytecode instanceof Bytecode.Invoke)
				return end(buildInvoke((Location<Bytecode.Invoke>) location).get(0));
			throw new CompilerException(new WyilUnsupportedCompilerError(location));
		}


		/*------ buildMergeNode ------*/

		private BooleanVertexLeaf buildMerge(
				BiFunction<BooleanVertexLeaf, BooleanVertexLeaf, BooleanVertexLeaf> mergeBoolean,
				BooleanVertexLeaf tree1, BooleanVertexLeaf tree2) {
			return mergeBoolean.apply(tree1, tree2);
		}
		private VertexLeaf<?> buildMerge(
				BiFunction<VertexLeaf<?>, VertexLeaf<?>, VertexLeaf<?>> mergeGeneral,
				VertexLeaf<?> tree1, VertexLeaf<?> tree2) {
			return mergeGeneral.apply(tree1, tree2);
		}
		private VertexSimpleRecord buildMerge(
				BiFunction<BooleanVertexLeaf, BooleanVertexLeaf, BooleanVertexLeaf> mergeBoolean,
				BiFunction<VertexLeaf<?>, VertexLeaf<?>, VertexLeaf<?>> mergeGeneral,
				VertexSimpleRecord tree1, VertexSimpleRecord tree2) throws CompilerException {
			return new VertexSimpleRecord(tree1.getFields().addComponent(tree2.getFields().takeSecond()).map23_(
				(t, f) -> buildMerge(mergeBoolean, mergeGeneral, t,f)));
		}
		private VertexSimpleUnion buildMerge(
				BiFunction<BooleanVertexLeaf, BooleanVertexLeaf, BooleanVertexLeaf> mergeBoolean,
				BiFunction<VertexLeaf<?>, VertexLeaf<?>, VertexLeaf<?>> mergeGeneral,
				VertexSimpleUnion tree1, VertexSimpleUnion tree2) throws CompilerException {
			return new VertexSimpleUnion(tree1.getOptions().gather(tree2.getOptions()).map_(
				(t, f) -> new VertexOption<>(
						 buildMerge(mergeBoolean, t.getFirstOperand(),f.getFirstOperand()),
						 buildMerge(mergeGeneral, t.getSecondOperand(), f.getSecondOperand()))));
		}
		private VertexRecordUnion buildMerge(
				BiFunction<BooleanVertexLeaf, BooleanVertexLeaf, BooleanVertexLeaf> mergeBoolean,
				BiFunction<VertexLeaf<?>, VertexLeaf<?>, VertexLeaf<?>> mergeGeneral,
				VertexRecordUnion tree1, VertexRecordUnion tree2) throws CompilerException {
			return new VertexRecordUnion(
				tree2.getSharedFields().duplicateFirst().mapSecond_(tree1::getSharedField).map23(
					(t, f) -> buildMerge(mergeBoolean, mergeGeneral, t,f)),
				tree2.getSpecificFields().duplicateFirst().mapSecond_(tree1::getSpecificField).map23(
					(t, f) -> new VertexOption<>(
							buildMerge(mergeBoolean, t.getFirstOperand(), f.getFirstOperand()),
							buildMerge(mergeBoolean, mergeGeneral, t.getSecondOperand(), f.getSecondOperand()))));
		}
		private VertexUnion buildMerge(
				BiFunction<BooleanVertexLeaf, BooleanVertexLeaf, BooleanVertexLeaf> mergeBoolean,
				BiFunction<VertexLeaf<?>, VertexLeaf<?>, VertexLeaf<?>> mergeGeneral,
				VertexUnion tree1, VertexUnion tree2) throws CompilerException {
			return new VertexUnion(
				buildMerge(mergeBoolean, mergeGeneral, tree1.getFirstOperand(), tree2.getFirstOperand()),
				new VertexOption<>(
						buildMerge(mergeBoolean, tree1.getHasRecords(), tree2.getHasRecords()),
						buildMerge(mergeBoolean, mergeGeneral, tree1.getRecordOptions(), tree2.getRecordOptions())));
		}
		private AccessibleVertexTree<?> buildMerge(
				BiFunction<BooleanVertexLeaf, BooleanVertexLeaf, BooleanVertexLeaf> mergeBoolean,
				BiFunction<VertexLeaf<?>, VertexLeaf<?>, VertexLeaf<?>> mergeGeneral,
				AccessibleVertexTree<?> tree1, AccessibleVertexTree<?> tree2) throws CompilerException {
			tree1.checkIdenticalStructure(tree2);
			if (tree1 instanceof BooleanVertexLeaf 	&& tree2 instanceof BooleanVertexLeaf)
				return buildMerge(mergeBoolean, (BooleanVertexLeaf)tree1, 	(BooleanVertexLeaf)tree2);
			if (tree1 instanceof VertexLeaf 			&& tree2 instanceof VertexLeaf)
				return buildMerge(mergeGeneral, (VertexLeaf<?>)tree1, 		(VertexLeaf<?>)tree2);
			if (tree1 instanceof VertexSimpleRecord 	&& tree2 instanceof VertexRecord)
				return buildMerge(mergeBoolean, mergeGeneral, (VertexSimpleRecord)tree1, 	(VertexSimpleRecord)tree2);
			if (tree1 instanceof VertexSimpleUnion 	&& tree2 instanceof VertexSimpleUnion)
				return buildMerge(mergeBoolean, mergeGeneral, (VertexSimpleUnion)tree1, 	(VertexSimpleUnion)tree2);
			if (tree1 instanceof VertexRecordUnion 	&& tree2 instanceof VertexRecordUnion)
				return buildMerge(mergeBoolean, mergeGeneral, (VertexRecordUnion)tree1, (VertexRecordUnion)tree2);
			if (tree1 instanceof VertexUnion 	&& tree2 instanceof VertexUnion)
				return buildMerge(mergeBoolean, mergeGeneral, (VertexUnion)tree1, (VertexUnion)tree2);
			throw UnsupportedTreeNodeCompilerError.exception(tree1);
		}



		private AccessibleVertexTree<?> buildMerge(
				BiFunction<HalfArrow<?>, HalfArrow<?>, DataNode> merge,
				AccessibleVertexTree<?> tree1, AccessibleVertexTree<?> tree2) throws CompilerException {
			return buildMerge(
				(b, c) -> new BooleanVertexLeaf(merge.apply(b.getValue(), c.getValue())),
				(b, c) -> new GeneralVertexLeaf(merge.apply(b.getValue(), c.getValue())),
				tree1, tree2);
		}



		private AccessibleVertexTree<?> buildEndIf(Location<Bytecode.If> ifs, HalfArrow<?> ifn, AccessibleVertexTree<?> trueLab, AccessibleVertexTree<?> falseLab) throws CompilerException {
			return buildMerge((t,f) -> t.node == f.node ? t.node : graph.new EndIfNode(ifs, ifn, t, f), trueLab, falseLab);
		}


//		private AccessibleVertexTree<?> copyNamedHalfArrow(AccessibleVertexTree<?> name, AccessibleVertexTree<?> node) throws CompilerException {
//			return buildMerge(
//				(na,no) -> new BooleanVertexLeaf(no.getValue().node, na.getValue().ident),
//				(na,no) -> new BooleanVertexLeaf(no.getValue().node, na.getValue().ident),
//				name,node);
//		}



		private void buildIf(Location<Bytecode.If> ifs) throws CompilerException {
			AccessibleVertexTree<?> cond = buildExpression(ifs.getOperand(0));

			if (!(cond instanceof VertexLeaf)) // TODO boolean ?
				throw new CompilerException(new CompilerError() {
					@Override
					public String info() {
						return "The condition is not a Leaf...";
					}
				});
			HalfArrow<?> ifn = ((VertexLeaf<?>)cond).getValue(); // TODO verif bool primitif.

			HashMap<Integer, AccessibleVertexTree<?>> state = new HashMap<>();
			vars.forEach(state::put);
			HashMap<Integer, AccessibleVertexTree<?>> tbc = new HashMap<>();

			PartialReturn prevReturn = partialReturn;
			partialReturn = null;
			build(ifs.getBlock(0));
			state.forEach((i, t) -> {if (vars.get(i) != t) tbc.put(i, vars.get(i));});

			vars.clear();
			state.forEach(vars::put);


			HashMap<Integer, AccessibleVertexTree<?>> fbc = new HashMap<>();

			PartialReturn trueReturn = partialReturn;
			partialReturn = null;
			if (ifs.getBytecode().hasFalseBranch())
				build(ifs.getBlock(1));
			state.forEach((i, t) -> {if (vars.get(i) != t) fbc.put(i, vars.get(i));});

			vars.clear();
//
//			debugLevel("True "+tbc.toString());
//			debugLevel("False "+fbc.toString());
//			debugLevel("State "+state.toString());
//			Generators.fromMap(state).forEach_(
//				(i, t) -> {
//					AccessibleVertexTree r = copyNamedHalfArrow(t, buildEndIf(ifs, ifn, tbc.getOrDefault(i, t), fbc.getOrDefault(i, t)));
//					vars.put(i, r);
//					debugLevel(tbc.getOrDefault(i, t).toString("T "));
//					debugLevel(fbc.getOrDefault(i, t).toString("F "));
//					debugLevel(t.toString("S "));
//					debugLevel(r.toString("R "));
//			});
			Generators.fromMap(state).forEach_(
				(i, t) -> vars.put(i, buildNamedHalfArrow(identifiers.get(i), buildEndIf(ifs, ifn, tbc.getOrDefault(i, t), fbc.getOrDefault(i, t)))));
			if (trueReturn != null || partialReturn != null) {
				addMessage(new NestedReturnCompilerNotice());
				trueReturn = new PartialReturn(ifs, ifn, trueReturn, partialReturn);
			}
			partialReturn = prevReturn != null ? prevReturn.completeReturn(trueReturn) : trueReturn;
		}



		/*------ buildStartWhile ------*/

		private BooleanVertexLeaf buildStartWhile(Location<Bytecode.While> whiles, BooleanVertexLeaf node) {
			return new BooleanVertexLeaf(graph.new WhileNode(whiles, node.getValue()));
		}
		private VertexLeaf<?> buildStartWhile(Location<Bytecode.While> whiles, VertexLeaf<?> node) {
			return new GeneralVertexLeaf(graph.new WhileNode(whiles, node.getValue()));
		}
		private VertexSimpleRecord buildStartWhile(Location<Bytecode.While> whiles, VertexSimpleRecord node) throws CompilerException {
			return new VertexSimpleRecord(node.getFields().mapSecond_(f -> buildStartWhile(whiles, f)));
		}
		private VertexSimpleUnion buildStartWhile(Location<Bytecode.While> whiles, VertexSimpleUnion node) throws CompilerException {
			return new VertexSimpleUnion(node.getOptions().map_(o -> new VertexOption<>(
						buildStartWhile(whiles, o.getFirstOperand()),
						buildStartWhile(whiles, o.getSecondOperand()))));
		}
		private VertexRecordUnion buildStartWhile(Location<Bytecode.While> whiles, VertexRecordUnion node) throws CompilerException {
			return new VertexRecordUnion(
				node.getSharedFields().mapSecond_(f -> buildStartWhile(whiles, f)),
				node.getSpecificFields().mapSecond_(
					o -> new VertexOption<>(
							buildStartWhile(whiles, o.getFirstOperand()),
							buildStartWhile(whiles, o.getSecondOperand()))));
		}
		private VertexUnion buildStartWhile(Location<Bytecode.While> whiles, VertexUnion node) throws CompilerException {
			return new VertexUnion(
				buildStartWhile(whiles, node.getFirstOperand()),
				new VertexOption<>(
						buildStartWhile(whiles, node.getHasRecords()),
						buildStartWhile(whiles, node.getRecordOptions())));
		}
		AccessibleVertexTree<?> buildStartWhile(Location<Bytecode.While> whiles, AccessibleVertexTree<?> node) throws CompilerException {
			if (node instanceof BooleanVertexLeaf)
				return buildStartWhile(whiles, (BooleanVertexLeaf)node);
			if (node instanceof VertexLeaf)
				return buildStartWhile(whiles, (VertexLeaf<?>)node);
			if (node instanceof VertexSimpleRecord)
				return buildStartWhile(whiles, (VertexSimpleRecord)node);
			if (node instanceof VertexSimpleUnion)
				return buildStartWhile(whiles, (VertexSimpleUnion)node);
			if (node instanceof VertexRecordUnion)
				return buildStartWhile(whiles, (VertexRecordUnion)node);
			if (node instanceof VertexUnion)
				return buildStartWhile(whiles, (VertexUnion)node);
			throw UnsupportedTreeNodeCompilerError.exception(node);
		}



		private AccessibleVertexTree<?> buildEndWhile(Location<Bytecode.While> whiles, HalfArrow<?> whilen, AccessibleVertexTree<?> previousValue, AccessibleVertexTree<?> nextValue) throws CompilerException {
			return buildMerge((p,n) -> {
				if (p.node != n.node) return graph.new EndWhileNode(whiles, whilen, p, n);
				if (p.node.targets.size() != 0) graph.new EndWhileNode(whiles, whilen, p, n);
				return p.node.sources.get(0).from;
			}, previousValue, nextValue);
		}







		private void buildWhile(Location<Bytecode.While> whiles) throws CompilerException {
			HashMap<Integer, AccessibleVertexTree<?>> pstate = new HashMap<>();
			Generators.fromMap(vars).forEach(pstate::put);
			HashMap<Integer, AccessibleVertexTree<?>> state = new HashMap<>();
			Generators.fromMap(vars).mapSecond_(t -> buildStartWhile(whiles, t)).duplicateFirst().mapSecond(identifiers::get).map23(this::buildNamedHalfArrow).forEach(state::put);
			Generators.fromMap(state).forEach(vars::put);
			AccessibleVertexTree<?> cond = buildExpression(whiles.getOperand(0));
			if (!(cond instanceof VertexLeaf)) // TODO boolean ?
				throw new CompilerException(new CompilerError() {
					@Override
					public String info() {
						return "The condition is not a Leaf...";
					}
				});
			HalfArrow<?> whilen = ((VertexLeaf<?>)cond).getValue(); // TODO verif bool primitif.

			build(whiles.getBlock(0));

			boolean m = false;
			for (Integer v : state.keySet()) {
				AccessibleVertexTree<?> p = state.get(v);
				AccessibleVertexTree<?> n = vars.get(v);
				if (p != n) m = true;
				vars.put(v, buildNamedHalfArrow(identifiers.get(v), buildEndWhile(whiles, whilen, p, n)));
			}

			if (!m)
				logger.addMessage(new CompilerNotice() {
					@Override
					public String info() {
						return "The loop <"+whiles+"> is infinite.";
					}});

		}
	}






	public DataFlowGraph buildGraph(FunctionOrMethod func) throws CompilerException {
		return new Builder(func).getGraph();
	}
}
