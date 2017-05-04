package wyvc.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class OrientedGraph<N extends OrientedGraph.Node<N,A>, A extends OrientedGraph.Arrow<A,N>> {
	public abstract static class Node<N extends Node<N,A>, A extends Arrow<A,N>> {
		public final List<A> sources = new ArrayList<>();
		public final List<A> targets = new ArrayList<>();
		public final OrientedGraph<N,A> graph;

		@SuppressWarnings("unchecked")
		public Node(OrientedGraph<N,A> graph) {
			this.graph = graph;
			graph.addNode((N)this);
		}

		public void addSource(A source) {
			sources.add(source);
		}

		public void addTarget(A target) {
			targets.add(target);
		}

		public List<N> getSources() {
			return Utils.convert(sources, (A a) -> a.from);
		}

		public List<N> getTargets() {
			return Utils.convert(targets, (A a) -> a.to);
		}
	}

	public abstract static class Arrow<A extends Arrow<A,N>, N extends Node<N,A>> {
		public final N from;
		public final N to;
		public OrientedGraph<N,A> graph = null;

		@SuppressWarnings("unchecked")
		public Arrow(OrientedGraph<N,A> graph, N from, N to) {
			this.from = from;
			this.to = to;
			this.graph = graph;
			graph.addArrow((A)this);
			from.addTarget((A) this);
			to.addSource((A) this);
		}
	}



	public final Set<N> nodes = new HashSet<>();
	public final Set<A> arrows = new HashSet<>();


	public final void addNode(N node) {
		nodes.add(node);
		nodeAdded(node);
	}

	public final void addArrow(A arrow) {
		arrows.add(arrow);
		arrowAdded(arrow);
	}

	public void nodeAdded(N node){}
	public void arrowAdded(A arrow){}



}